package com.example.androidapp.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource


data class DeviceLocation(
    val latitude: Double,
    val longitude: Double
)


class DeviceLocationRepository(
    private val context: Context
) {

    private val fusedLocationClient:
            FusedLocationProviderClient =
        LocationServices
            .getFusedLocationProviderClient(context)


    private var locationCallback:
            LocationCallback? = null


    private fun hasLocationPermission(): Boolean {

        val fineGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED


        val coarseGranted =
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED


        return fineGranted || coarseGranted
    }


    // ---------------------------------------------------------
    // ONE-TIME LOCATION
    // ---------------------------------------------------------

    fun getCurrentLocation(
        onResult: (Result<DeviceLocation>) -> Unit
    ) {

        if (!hasLocationPermission()) {

            onResult(
                Result.failure(
                    SecurityException(
                        "Location permission has not been granted."
                    )
                )
            )

            return
        }


        try {

            val cancellationTokenSource =
                CancellationTokenSource()


            fusedLocationClient
                .getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    cancellationTokenSource.token
                )
                .addOnSuccessListener { location ->

                    if (location != null) {

                        onResult(
                            Result.success(
                                DeviceLocation(
                                    latitude =
                                        location.latitude,

                                    longitude =
                                        location.longitude
                                )
                            )
                        )

                    } else {

                        onResult(
                            Result.failure(
                                Exception(
                                    "Current location is unavailable."
                                )
                            )
                        )
                    }
                }
                .addOnFailureListener { exception ->

                    onResult(
                        Result.failure(exception)
                    )
                }

        } catch (exception: SecurityException) {

            onResult(
                Result.failure(exception)
            )
        }
    }


    // ---------------------------------------------------------
    // LIVE LOCATION UPDATES
    // ---------------------------------------------------------

    fun startLocationUpdates(
        onLocationUpdate: (DeviceLocation) -> Unit,
        onError: (Exception) -> Unit
    ) {

        if (!hasLocationPermission()) {

            onError(
                SecurityException(
                    "Location permission is required for live sharing."
                )
            )

            return
        }


        // Prevent multiple callbacks from being registered.
        stopLocationUpdates()


        val locationRequest =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                10_000L
            )
                .setMinUpdateIntervalMillis(
                    5_000L
                )
                .setMinUpdateDistanceMeters(
                    10f
                )
                .build()


        val callback =
            object : LocationCallback() {

                override fun onLocationResult(
                    result: LocationResult
                ) {

                    val location =
                        result.lastLocation
                            ?: return


                    onLocationUpdate(
                        DeviceLocation(
                            latitude =
                                location.latitude,

                            longitude =
                                location.longitude
                        )
                    )
                }
            }


        locationCallback =
            callback


        try {

            fusedLocationClient
                .requestLocationUpdates(
                    locationRequest,
                    callback,
                    Looper.getMainLooper()
                )
                .addOnFailureListener { exception ->

                    onError(exception)
                }

        } catch (exception: SecurityException) {

            onError(exception)
        }
    }


    // ---------------------------------------------------------
    // STOP LIVE UPDATES
    // ---------------------------------------------------------

    fun stopLocationUpdates() {

        val callback =
            locationCallback
                ?: return


        fusedLocationClient
            .removeLocationUpdates(
                callback
            )


        locationCallback =
            null
    }
}