package com.dnfapps.arrmatey.backup.usecase

import com.dnfapps.arrmatey.backup.TransportEncryptor
import com.dnfapps.arrmatey.backup.model.BackupExport
import com.dnfapps.arrmatey.database.EncryptedString
import com.dnfapps.arrmatey.database.dao.InsertResult
import com.dnfapps.arrmatey.database.dao.InstanceDao
import com.dnfapps.arrmatey.datastore.InstancePreferenceStoreRepository
import com.dnfapps.arrmatey.datastore.PreferencesStore
import com.dnfapps.arrmatey.downloadclient.database.DownloadClientDao
import com.dnfapps.arrmatey.downloadclient.model.DownloadClient
import com.dnfapps.arrmatey.instances.model.Instance
import com.dnfapps.arrmatey.instances.model.InstanceType
import com.dnfapps.arrmatey.webpage.model.CustomWebpage
import com.dnfapps.arrmatey.webpage.repository.CustomWebpageRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

class ImportDataUseCase(
    private val instanceDao: InstanceDao,
    private val downloadClientDao: DownloadClientDao,
    private val instancePreferenceStoreRepository: InstancePreferenceStoreRepository,
    private val preferencesStore: PreferencesStore,
    private val customWebpageRepository: CustomWebpageRepository,
    private val transportEncryptor: TransportEncryptor,
    private val json: Json,
) {
    fun decryptBackup(
        encryptedData: String,
        password: String,
    ): BackupExport {
        val jsonString = transportEncryptor.decrypt(encryptedData, password)
        val sanitizedJson = sanitizeLegacyBackupJson(jsonString)
        return json.decodeFromString(sanitizedJson)
    }

    private fun sanitizeLegacyBackupJson(jsonString: String): String =
        jsonString
            .replace("\"Booksehlf\"", "\"Bookshelf\"")
            .replace("\"Booksehelf\"", "\"Bookshelf\"")
            .replace("\"booksehlf\"", "\"bookshelf\"")
            .replace("\"booksehelf\"", "\"bookshelf\"")

    suspend fun importSelected(
        backup: BackupExport,
        selectedInstanceIndices: Set<Int>,
        selectedDownloadClientIndices: Set<Int>,
        selectedCustomWebpageIndices: Set<Int>,
        importTabPreferences: Boolean,
        importUiPreferences: Boolean,
    ) {
        backup.instances.forEachIndexed { index, export ->
            if (index in selectedInstanceIndices) {
                val instance =
                    Instance(
                        type = export.type,
                        label = export.label,
                        url = export.url,
                        apiKey = EncryptedString(export.apiKey),
                        noApiKeyRequired = export.noApiKeyRequired,
                        enabled = export.enabled,
                        slowInstance = export.slowInstance,
                        customTimeout = export.customTimeout,
                        notificationsEnabled = export.notificationsEnabled,
                        headers = export.headers,
                        localNetworkEnabled = export.localNetworkEnabled,
                        localNetworkSsids = export.localNetworkSsids,
                        localNetworkEndpoint = export.localNetworkEndpoint,
                    )

                val existingByUrl = instanceDao.findByUrl(instance.url)
                val existingByLabel = instanceDao.findByLabel(instance.label)

                val finalInstance =
                    when {
                        existingByUrl != null -> {
                            var uniqueLabel = instance.label
                            if (existingByLabel != null && existingByLabel != existingByUrl) {
                                uniqueLabel = "${instance.label} (Imported)"
                            }
                            instance.copy(id = existingByUrl, label = uniqueLabel)
                        }
                        existingByLabel != null -> {
                            instance.copy(id = existingByLabel)
                        }
                        else -> {
                            instance
                        }
                    }

                val id =
                    if (finalInstance.id != 0L) {
                        instanceDao.update(finalInstance)
                        finalInstance.id
                    } else {
                        instanceDao.insert(finalInstance)
                    }

                if (id > 0 && export.preferences != null) {
                    val prefStore = instancePreferenceStoreRepository.getInstancePreferences(id)
                    prefStore.savePreferences(export.preferences)
                }
            }
        }

        InstanceType.entries.forEach { type ->
            instanceDao.ensureFirstSelectedIfNone(type)
        }

        backup.downloadClients.forEachIndexed { index, export ->
            if (index in selectedDownloadClientIndices) {
                val client =
                    DownloadClient(
                        type = export.type,
                        label = export.label,
                        url = export.url,
                        username = EncryptedString(export.username),
                        password = EncryptedString(export.password),
                        apiKey = EncryptedString(export.apiKey),
                        noApiKeyRequired = export.noApiKeyRequired,
                        headers = export.headers,
                        localNetworkEnabled = export.localNetworkEnabled,
                        localNetworkSsids = export.localNetworkSsids,
                        localNetworkEndpoint = export.localNetworkEndpoint,
                    )

                val existingByUrl = downloadClientDao.findByUrl(client.url)
                val existingByLabel = downloadClientDao.findByLabel(client.label)

                val finalClient =
                    when {
                        existingByUrl != null -> {
                            var uniqueLabel = client.label
                            if (existingByLabel != null && existingByLabel != existingByUrl) {
                                uniqueLabel = "${client.label} (Imported)"
                            }
                            client.copy(id = existingByUrl, label = uniqueLabel)
                        }
                        existingByLabel != null -> {
                            client.copy(id = existingByLabel)
                        }
                        else -> {
                            client
                        }
                    }

                if (finalClient.id != 0L) {
                    downloadClientDao.update(finalClient)
                } else {
                    downloadClientDao.insert(finalClient)
                }
            }
        }

        val webpageIdMap = mutableMapOf<Long, Long>()
        if (selectedCustomWebpageIndices.isNotEmpty()) {
            val existingWebpages = customWebpageRepository.getAllWebpages().first().toMutableList()
            backup.customWebpages.forEachIndexed { index, export ->
                if (index in selectedCustomWebpageIndices) {
                    val imported =
                        CustomWebpage(
                            name = export.name,
                            url = export.url,
                            headers = export.headers,
                        )
                    val existing = existingWebpages.firstOrNull { it.url == export.url }

                    val result =
                        if (existing != null) {
                            customWebpageRepository.updateWebpage(imported.copy(id = existing.id))
                        } else {
                            customWebpageRepository.addWebpage(imported)
                        }
                    val localId =
                        when (result) {
                            is InsertResult.Success -> result.id
                            is InsertResult.Conflict -> 0L
                            is InsertResult.Error -> 0L
                        }

                    if (localId > 0L) {
                        webpageIdMap[export.id] = localId
                        existingWebpages.removeAll { it.id == localId }
                        existingWebpages.add(imported.copy(id = localId))
                    }
                }
            }
        }

        backup.globalPreferences?.let { global ->
            if (importTabPreferences) {
                global.tabPreferences?.let { tabPreferences ->
                    val selectedBackupIds =
                        selectedCustomWebpageIndices.mapNotNull { backup.customWebpages.getOrNull(it)?.id }.toSet()

                    fun remap(keys: List<String>): List<String> =
                        keys.mapNotNull { key ->
                            if (!key.startsWith("webpage_")) {
                                key
                            } else {
                                val backupId = key.removePrefix("webpage_").toLongOrNull()
                                when {
                                    backupId == null -> key
                                    backupId in selectedBackupIds -> webpageIdMap[backupId]?.let { "webpage_$it" }
                                    else -> null
                                }
                            }
                        }

                    preferencesStore.saveTabPreferences(
                        tabPreferences.copy(
                            orderedVisibleKeys = remap(tabPreferences.orderedVisibleKeys),
                            orderedHiddenKeys = remap(tabPreferences.orderedHiddenKeys),
                            orderedRemovedKeys = remap(tabPreferences.orderedRemovedKeys),
                        ),
                    )
                }
            }
            if (importUiPreferences) {
                global.useServiceNavLogos?.let { preferencesStore.setUseServiceNavLogos(it) }
                global.hideInstanceSwitcher?.let { preferencesStore.setHideInstanceSwitcher(it) }
            }
        }
    }
}
