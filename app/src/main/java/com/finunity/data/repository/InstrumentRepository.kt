package com.finunity.data.repository

import com.finunity.data.remote.NetworkModule
import com.finunity.data.remote.ServiceInstrument
import com.finunity.data.remote.requireData

class InstrumentRepository {
    suspend fun search(query: String, assetType: String): List<ServiceInstrument> {
        val normalized = query.trim()
        require(normalized.isNotBlank()) { "请输入证券名称或代码" }
        val envelope = NetworkModule.authorized { api ->
            api.searchInstruments(
                query = normalized,
                types = assetType,
                limit = 20
            )
        }
        return envelope.requireData().items.map { instrument ->
            require(instrument.instrumentId.isNotBlank()) {
                "[INVALID_INSTRUMENT] 服务返回空 instrument_id (requestId=${envelope.requestId})"
            }
            require(instrument.canonicalSymbol.isNotBlank() && instrument.name.isNotBlank()) {
                "[INVALID_INSTRUMENT] 服务返回的代码或名称为空 (requestId=${envelope.requestId})"
            }
            instrument
        }
    }
}
