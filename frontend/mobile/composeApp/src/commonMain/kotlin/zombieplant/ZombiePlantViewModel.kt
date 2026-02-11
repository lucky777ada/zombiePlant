
package zombieplant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ZombiePlantViewModel : ViewModel() {

    private val api = ZombiePlantAPI()

    private val _hardwareStatus = MutableStateFlow<HardwareStatusResponse?>(null)
    val hardwareStatus: StateFlow<HardwareStatusResponse?> = _hardwareStatus.asStateFlow()

    private val _lastUpdated = MutableStateFlow<Long>(0)
    val lastUpdated: StateFlow<Long> = _lastUpdated.asStateFlow()

    private val _plantImage = MutableStateFlow<ByteArray?>(null)
    val plantImage: StateFlow<ByteArray?> = _plantImage.asStateFlow()

    private val _latestTimelapse = MutableStateFlow<ByteArray?>(null)
    val latestTimelapse: StateFlow<ByteArray?> = _latestTimelapse.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun fetchData(platform: Platform) {
        viewModelScope.launch {
            if (_hardwareStatus.value == null) {
                _isLoading.value = true
            }
            try {
                _hardwareStatus.value = api.getHardwareStatus()
                _plantImage.value = api.getPlantImage()
                _lastUpdated.value = platform.currentTimeMillis()
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleAcRelay(state: String, platform: Platform) {
        viewModelScope.launch {
            try {
                api.controlAcRelay(state)
                fetchData(platform)
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
            }
        }
    }

    fun togglePump(pumpId: String, duration: Float, platform: Platform) {
        viewModelScope.launch {
            try {
                api.controlPump(pumpId, duration)
                fetchData(platform)
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
            }
        }
    }

    fun fetchLatestTimelapse() {
        viewModelScope.launch {
            try {
                _latestTimelapse.value = api.getLatestTimelapse()
            } catch (e: Exception) {
                // Handle error
                e.printStackTrace()
            }
        }
    }
}
