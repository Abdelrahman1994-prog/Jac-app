package com.jacdrive.obd.di

import android.content.Context
import com.jacdrive.core.di.IoDispatcher
import com.jacdrive.obd.bluetooth.BluetoothClassicTransport
import com.jacdrive.obd.bluetooth.ObdTransportFactory
import com.jacdrive.obd.bluetooth.TransportType
import com.jacdrive.obd.elm.ObdException
import com.jacdrive.obd.elm.ObdFailure
import com.jacdrive.obd.repository.DefaultVehicleDataRepository
import com.jacdrive.obd.repository.VehicleDataRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher

@Module
@InstallIn(SingletonComponent::class)
abstract class ObdModule {
    @Binds
    abstract fun vehicleDataRepository(impl: DefaultVehicleDataRepository): VehicleDataRepository

    companion object {
        @Provides
        fun transportFactory(@ApplicationContext context: Context, @IoDispatcher io: CoroutineDispatcher): ObdTransportFactory =
            ObdTransportFactory { device ->
                when (device.type) {
                    TransportType.BLUETOOTH_CLASSIC -> BluetoothClassicTransport(context, device.address, io)
                    // BLE adapters plug in here with a GATT-backed ObdTransport.
                    TransportType.BLE -> throw ObdException(ObdFailure.UNSUPPORTED_TRANSPORT)
                }
            }
    }
}
