package com.jacdrive.obd.di

import com.jacdrive.obd.repository.DefaultVehicleDataRepository
import com.jacdrive.obd.repository.VehicleDataRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class ObdModule {
    @Binds
    abstract fun vehicleDataRepository(impl: DefaultVehicleDataRepository): VehicleDataRepository
}
