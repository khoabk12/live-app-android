package com.hypertrack.backend

import android.os.Build
import android.util.Log
import com.android.volley.NetworkResponse
import com.android.volley.Response
import com.android.volley.VolleyError
import com.android.volley.toolbox.HttpHeaderParser
import com.android.volley.toolbox.JsonRequest
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset

private const val ADDRESS = "https://live-app-backend.htprod.hypertrack.com/"
private const val LIVE_APP_BACKEND_DEVICES_ENDPOINT = ADDRESS + "devices/"
private const val LIVE_APP_BACKEND_TRIPS_ENDPOINT = ADDRESS + "trips/"

private const val TAG = "Requests"

class StartTrackingRequest(
        deviceId: String,
        tokenString: String,
        responseListener: Response.Listener<Void>,
        errorListener: Response.ErrorListener
) : LiveAppBackendRequest<Void>(
        tokenString, "$LIVE_APP_BACKEND_DEVICES_ENDPOINT$deviceId/start", "",
        responseListener, errorListener
) {
    override fun parseNetworkResponse(response: NetworkResponse?): Response<Void> {
        response?.let { if (isSuccessFamilyStatus(it)) return Response.success(null, null) }
        Log.e(TAG, "Got status code ${response?.statusCode}, body ${response?.toString()}")
        return Response.error(VolleyError(response))
    }

}

class StopTrackingRequest(
        deviceId: String,
        tokenString: String,
        responseListener: Response.Listener<Void>,
        errorListener: Response.ErrorListener
) : LiveAppBackendRequest<Void>(
        tokenString, "$LIVE_APP_BACKEND_DEVICES_ENDPOINT$deviceId/stop", "",
        responseListener, errorListener
) {
    override fun parseNetworkResponse(response: NetworkResponse?): Response<Void> {
        response?.let { if (isSuccessFamilyStatus(it)) return Response.success(null, null) }
        Log.e(TAG, "Got status code ${response?.statusCode}, body ${response?.toString()}")
        return Response.error(VolleyError(response))
    }

}

class CreateTripRequest(tripConfig: TripConfig, private val gson: Gson, tokenString: String,
                        responseListener: Response.Listener<ShareableTrip>, errorListener: Response.ErrorListener
) :
        LiveAppBackendRequest<ShareableTrip>(tokenString, LIVE_APP_BACKEND_TRIPS_ENDPOINT,
                tripConfig.getRequestBody(), responseListener, errorListener
        ) {

    override fun parseNetworkResponse(response: NetworkResponse?): Response<ShareableTrip> {
        response?.let {

            // Expired token etc.
            if (!isSuccessFamilyStatus(response)) {
                return Response.error(VolleyError(response))
            }

            try {
                val responseBody = String(it.data, Charset.forName(HttpHeaderParser.parseCharset(it.headers, Charsets.UTF_8.name())))
                if (responseBody.isEmpty()) {
                    return Response.error(VolleyError("Can't create trip from empty response"))
                }
                val parsedTrip = gson.fromJson<Trip>(responseBody, Trip::class.java)
                parsedTrip?.let { trip ->
                    return Response.success(
                            ShareableTrip(trip.views.shareUrl, trip.views.embedUrl, trip.tripId, trip.estimate?.route?.remainingDuration),
                            HttpHeaderParser.parseCacheHeaders(response)
                    )
                }
            } catch (error: UnsupportedEncodingException) {
                return Response.error(VolleyError(error))
            } catch (error: JsonSyntaxException) {
                return Response.error(VolleyError(error))
            }
        }
        return Response.error(VolleyError("Can't create shareable trip without response"))
    }
}

class CompleteTripRequest(private val tripId: String, tokenString: String,
                          responseListener: Response.Listener<Void>,
                          errorListener: Response.ErrorListener) :
        LiveAppBackendRequest<Void>(tokenString,
                "$LIVE_APP_BACKEND_TRIPS_ENDPOINT$tripId/complete", "",
                responseListener, errorListener) {

    override fun parseNetworkResponse(response: NetworkResponse?): Response<Void> {
        response?.let { if (isSuccessFamilyStatus(it)) return Response.success(null, null) }
        Log.e(TAG, "Got status code ${response?.statusCode}, body ${response?.toString()} url $LIVE_APP_BACKEND_TRIPS_ENDPOINT$tripId/complete")
        return Response.error(VolleyError(response))
    }
}

class GeofenceEventRequest(
        deviceId: String, eventName: String,
        tokenString: String
) : LiveAppBackendRequest<Void>(tokenString, "${ADDRESS}geofence",
        "{\"device_id\": \"$deviceId\", \"geofence_name\": \"Home\", \"geofence_action\": \"$eventName\"}",
        null, null
) {
    override fun parseNetworkResponse(response: NetworkResponse?): Response<Void> {
        response?.let { if (isSuccessFamilyStatus(it)) return Response.success(null, null) }
        Log.e(TAG, "Got status code ${response?.statusCode}, body ${response?.toString()} url ${ADDRESS}geofence")
        return Response.error(VolleyError(response))
    }

}

abstract class LiveAppBackendRequest<T>(private val tokenString: String, url: String, requestBody: String, responseListener: Response.Listener<T>?,
                                        errorListener: Response.ErrorListener?) : JsonRequest<T>(Method.POST, url, requestBody, responseListener, errorListener) {
    protected fun isSuccessFamilyStatus(networkResponse: NetworkResponse) = networkResponse.statusCode / 100 == 2

    override fun parseNetworkError(volleyError: VolleyError?): VolleyError {
        volleyError?.networkResponse?.let { Log.e(TAG, "Got error from url $url data ${String(it.data)}") }
        return super.parseNetworkError(volleyError)
    }

    override fun getHeaders(): MutableMap<String, String> {
        val defaultHeaders = super.getHeaders()
        val headers = HashMap<String, String>(defaultHeaders.size + 2)
        headers.putAll(defaultHeaders)
        headers["Authorization"] = "Bearer $tokenString"
        headers["User-Agent"] = "LiveApp/${BuildConfig.VERSION_NAME} Volley/1.1.1 Android/${Build.VERSION.RELEASE}"
        return headers
    }
}

private data class Views(
        @SerializedName("share_url") val shareUrl: String,
        @SerializedName("embed_url") val embedUrl: String
)

private data class Trip(
        @SerializedName("trip_id") val tripId: String,
        @SerializedName("views") val views: Views,
        @SerializedName("estimate") val estimate: Estimate?
)

private data class Estimate(
        @SerializedName("route") val route: Route?
)

private data class Route(
        @SerializedName("remaining_duration") val remainingDuration: Int?
)