package com.jacdrive.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jacdrive.core.database.VehicleProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FuelType { PETROL, DIESEL, HYBRID, ELECTRIC }

data class VehicleForm(
    val name: String = "",
    val modelYear: String = "",
    val trim: String = "",
    val mileageKm: String = "",
    val vin: String = "",
    val fuelType: FuelType = FuelType.PETROL,
    val engine: String = "",
    val transmission: String = "",
) {
    val vinError: Boolean get() = vin.isNotBlank() && !VinValidator.isValid(vin)
    val yearError: Boolean get() = modelYear.toIntOrNull()?.let { it !in 1990..2100 } ?: true
    val mileageError: Boolean get() = mileageKm.toIntOrNull()?.let { it < 0 } ?: true
    val isValid: Boolean get() = name.isNotBlank() && !vinError && !yearError && !mileageError
}

data class VehicleUiState(
    val profile: VehicleProfileEntity? = null,
    val editing: Boolean = false,
    val form: VehicleForm = VehicleForm(),
)

@HiltViewModel
class VehicleViewModel @Inject constructor(
    private val repository: VehicleRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(VehicleUiState())
    val state: StateFlow<VehicleUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.profile.collect { p -> _state.update { it.copy(profile = p) } }
        }
    }

    fun startEditing() {
        val p = _state.value.profile ?: return
        _state.update {
            it.copy(
                editing = true,
                form = VehicleForm(
                    name = p.name,
                    modelYear = p.modelYear.toString(),
                    trim = p.trim,
                    mileageKm = p.currentMileageKm.toString(),
                    vin = p.vin.orEmpty(),
                    fuelType = runCatching { FuelType.valueOf(p.fuelType) }.getOrDefault(FuelType.PETROL),
                    engine = p.engineDescription.orEmpty(),
                    transmission = p.transmission.orEmpty(),
                ),
            )
        }
    }

    fun cancelEditing() = _state.update { it.copy(editing = false) }

    fun updateForm(transform: (VehicleForm) -> VehicleForm) = _state.update { it.copy(form = transform(it.form)) }

    fun save() {
        val form = _state.value.form
        if (!form.isValid) return
        viewModelScope.launch {
            val current = _state.value.profile ?: repository.profile.first()
            repository.save(
                current.copy(
                    name = form.name.trim(),
                    modelYear = form.modelYear.toInt(),
                    trim = form.trim.trim(),
                    currentMileageKm = form.mileageKm.toInt(),
                    vin = form.vin.trim().uppercase().ifBlank { null },
                    fuelType = form.fuelType.name,
                    engineDescription = form.engine.trim().ifBlank { null },
                    transmission = form.transmission.trim().ifBlank { null },
                ),
            )
            _state.update { it.copy(editing = false) }
        }
    }
}
