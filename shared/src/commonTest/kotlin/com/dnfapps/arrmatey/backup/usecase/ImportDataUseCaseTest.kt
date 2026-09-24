package com.dnfapps.arrmatey.backup.usecase

import com.dnfapps.arrmatey.backup.TransportEncryptor
import com.dnfapps.arrmatey.backup.model.BackupExport
import com.dnfapps.arrmatey.backup.model.CustomWebpageExport
import com.dnfapps.arrmatey.database.dao.CustomWebpageDao
import com.dnfapps.arrmatey.database.dao.InstanceDao
import com.dnfapps.arrmatey.datastore.DataStoreFactory
import com.dnfapps.arrmatey.datastore.InstancePreferenceStoreRepository
import com.dnfapps.arrmatey.datastore.PreferencesStore
import com.dnfapps.arrmatey.downloadclient.database.DownloadClientDao
import com.dnfapps.arrmatey.downloadclient.model.DownloadClient
import com.dnfapps.arrmatey.instances.model.Instance
import com.dnfapps.arrmatey.instances.model.InstanceType
import com.dnfapps.arrmatey.webpage.model.CustomWebpage
import com.dnfapps.arrmatey.webpage.repository.CustomWebpageRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ImportDataUseCaseTest {
    private val fakeEncryptor =
        object : TransportEncryptor {
            override fun encrypt(
                data: String,
                password: String,
            ): String = data

            override fun decrypt(
                encryptedData: String,
                password: String,
            ): String = encryptedData
        }

    private val json =
        Json {
            ignoreUnknownKeys = true
        }

    private val dummyInstanceDao =
        object : InstanceDao {
            override suspend fun insert(instance: Instance): Long = 0

            override suspend fun delete(instance: Instance) {}

            override suspend fun update(instance: Instance): Int = 0

            override suspend fun updateAll(instances: List<Instance>) {}

            override fun observeAllInstances(): Flow<List<Instance>> = emptyFlow()

            override fun observeInstancesByType(type: InstanceType): Flow<List<Instance>> = emptyFlow()

            override suspend fun getAllInstances(): List<Instance> = emptyList()

            override suspend fun getInstanceById(id: Long): Instance? = null

            override fun observeSelectedInstance(type: InstanceType): Flow<Instance?> = emptyFlow()

            override suspend fun getInstancesOfType(type: InstanceType): List<Instance> = emptyList()

            override suspend fun unselectAllOf(type: InstanceType) {}

            override suspend fun selectInstance(id: Long) {}

            override suspend fun findByUrl(url: String): Long? = null

            override suspend fun findByLabel(label: String): Long? = null

            override suspend fun findOtherByUrl(
                url: String,
                currentId: Long,
            ): Long? = null

            override suspend fun findOtherByLabel(
                label: String,
                currentId: Long,
            ): Long? = null

            override suspend fun ensureFirstSelectedIfNone(type: InstanceType) {}
        }

    private val dummyDownloadClientDao =
        object : DownloadClientDao {
            override suspend fun insert(downloadClient: DownloadClient): Long = 0

            override suspend fun delete(downloadClient: DownloadClient) {}

            override suspend fun update(downloadClient: DownloadClient): Int = 0

            override suspend fun updateAll(downloadClients: List<DownloadClient>) {}

            override fun observeAllDownloadClients(): Flow<List<DownloadClient>> = emptyFlow()

            override fun observeSelectedDownloadClient(): Flow<DownloadClient?> = emptyFlow()

            override suspend fun getDownloadClientById(id: Long): DownloadClient? = null

            override suspend fun getAllDownloadClients(): List<DownloadClient> = emptyList()

            override suspend fun findByUrl(url: String): Long? = null

            override suspend fun findByLabel(label: String): Long? = null

            override suspend fun findOtherByUrl(
                url: String,
                currentId: Long,
            ): Long? = null

            override suspend fun findOtherByLabel(
                label: String,
                currentId: Long,
            ): Long? = null

            override suspend fun unselectAll() {}

            override suspend fun selectDownloadClient(id: Long) {}

            override suspend fun ensureFirstSelectedIfNone() {}
        }

    private val dummyCustomWebpageDao =
        object : CustomWebpageDao {
            override fun getAllWebpages(): Flow<List<CustomWebpage>> = emptyFlow()

            override suspend fun getWebpageById(id: Long): CustomWebpage? = null

            override fun observeWebpageById(id: Long): Flow<CustomWebpage?> = emptyFlow()

            override suspend fun insert(webpage: CustomWebpage): Long = 0

            override suspend fun update(webpage: CustomWebpage): Int = 0

            override suspend fun delete(webpage: CustomWebpage) {}

            override suspend fun deleteById(id: Long) {}
        }

    private val customWebpageRepository = CustomWebpageRepository(dummyCustomWebpageDao)

    private val dataStoreFactory = DataStoreFactory()

    @Test
    fun testDecryptBackupWithLegacyBooksehlfTypo() {
        val legacyJsonBackup =
            """
            {
                "instances": [
                    {
                        "type": "Booksehlf",
                        "label": "My Books",
                        "url": "http://192.168.1.100:8787",
                        "apiKey": "test-key",
                        "noApiKeyRequired": false,
                        "enabled": true,
                        "slowInstance": false,
                        "notificationsEnabled": false,
                        "headers": [],
                        "localNetworkEnabled": false,
                        "localNetworkSsids": []
                    }
                ],
                "downloadClients": []
            }
            """.trimIndent()

        val importDataUseCase =
            ImportDataUseCase(
                instanceDao = dummyInstanceDao,
                downloadClientDao = dummyDownloadClientDao,
                instancePreferenceStoreRepository = InstancePreferenceStoreRepository(dataStoreFactory),
                preferencesStore = PreferencesStore(dataStoreFactory),
                customWebpageRepository = customWebpageRepository,
                transportEncryptor = fakeEncryptor,
                json = json,
            )

        val result = importDataUseCase.decryptBackup(legacyJsonBackup, "password")

        assertNotNull(result)
        assertEquals(1, result.version)
        assertEquals(0, result.customWebpages.size)
        assertEquals(1, result.instances.size)
        assertEquals(InstanceType.Bookshelf, result.instances.first().type)
        assertEquals("My Books", result.instances.first().label)
    }

    @Test
    fun testDecryptBackupWithLegacyBooksehelfTypo() {
        val legacyJsonBackup =
            """
            {
                "instances": [
                    {
                        "type": "Booksehelf",
                        "label": "My Books 2",
                        "url": "http://192.168.1.101:8787",
                        "apiKey": "test-key-2",
                        "noApiKeyRequired": false,
                        "enabled": true,
                        "slowInstance": false,
                        "notificationsEnabled": false,
                        "headers": [],
                        "localNetworkEnabled": false,
                        "localNetworkSsids": []
                    }
                ],
                "downloadClients": []
            }
            """.trimIndent()

        val importDataUseCase =
            ImportDataUseCase(
                instanceDao = dummyInstanceDao,
                downloadClientDao = dummyDownloadClientDao,
                instancePreferenceStoreRepository = InstancePreferenceStoreRepository(dataStoreFactory),
                preferencesStore = PreferencesStore(dataStoreFactory),
                customWebpageRepository = customWebpageRepository,
                transportEncryptor = fakeEncryptor,
                json = json,
            )

        val result = importDataUseCase.decryptBackup(legacyJsonBackup, "password")

        assertNotNull(result)
        assertEquals(1, result.instances.size)
        assertEquals(InstanceType.Bookshelf, result.instances.first().type)
        assertEquals("My Books 2", result.instances.first().label)
    }

    @Test
    fun testDecryptBackupWithCustomWebpage() {
        val backupJson =
            """
            {
                "version": 2,
                "instances": [],
                "downloadClients": [],
                "customWebpages": [
                    {
                        "id": 42,
                        "name": "Status",
                        "url": "https://status.example.com",
                        "headers": [
                            {
                                "key": "Authorization",
                                "value": "Bearer test"
                            }
                        ]
                    }
                ]
            }
            """.trimIndent()

        val importDataUseCase =
            ImportDataUseCase(
                instanceDao = dummyInstanceDao,
                downloadClientDao = dummyDownloadClientDao,
                instancePreferenceStoreRepository = InstancePreferenceStoreRepository(dataStoreFactory),
                preferencesStore = PreferencesStore(dataStoreFactory),
                customWebpageRepository = customWebpageRepository,
                transportEncryptor = fakeEncryptor,
                json = json,
            )

        val result = importDataUseCase.decryptBackup(backupJson, "password")

        assertEquals(2, result.version)
        assertEquals(1, result.customWebpages.size)
        val webpage = result.customWebpages.first()
        assertEquals(42L, webpage.id)
        assertEquals("Status", webpage.name)
        assertEquals("https://status.example.com", webpage.url)
        assertEquals(1, webpage.headers.size)
        assertEquals("Authorization", webpage.headers.first().key)
        assertEquals("Bearer test", webpage.headers.first().value)
    }

    @Test
    fun testImportSameNameDifferentUrlDoesNotOverwriteExistingWebpage() =
        runTest {
            val existing =
                CustomWebpage(
                    id = 5L,
                    name = "Status",
                    url = "https://local.example.com",
                )
            val webpages = MutableStateFlow(listOf(existing))
            val webpageDao =
                object : CustomWebpageDao {
                    override fun getAllWebpages(): Flow<List<CustomWebpage>> = webpages

                    override suspend fun getWebpageById(id: Long): CustomWebpage? =
                        webpages.value.firstOrNull { it.id == id }

                    override fun observeWebpageById(id: Long): Flow<CustomWebpage?> = emptyFlow()

                    override suspend fun insert(webpage: CustomWebpage): Long {
                        val id = (webpages.value.maxOfOrNull { it.id } ?: 0L) + 1L
                        webpages.value = webpages.value + webpage.copy(id = id)
                        return id
                    }

                    override suspend fun update(webpage: CustomWebpage): Int {
                        val index = webpages.value.indexOfFirst { it.id == webpage.id }
                        if (index < 0) return 0
                        webpages.value = webpages.value.toMutableList().also { it[index] = webpage }
                        return 1
                    }

                    override suspend fun delete(webpage: CustomWebpage) {
                        webpages.value = webpages.value.filterNot { it.id == webpage.id }
                    }

                    override suspend fun deleteById(id: Long) {
                        webpages.value = webpages.value.filterNot { it.id == id }
                    }
                }

            val importDataUseCase =
                ImportDataUseCase(
                    instanceDao = dummyInstanceDao,
                    downloadClientDao = dummyDownloadClientDao,
                    instancePreferenceStoreRepository = InstancePreferenceStoreRepository(dataStoreFactory),
                    preferencesStore = PreferencesStore(dataStoreFactory),
                    customWebpageRepository = CustomWebpageRepository(webpageDao),
                    transportEncryptor = fakeEncryptor,
                    json = json,
                )

            val backup =
                BackupExport(
                    version = 2,
                    customWebpages =
                        listOf(
                            CustomWebpageExport(
                                id = 42L,
                                name = "Status",
                                url = "https://backup.example.com",
                            ),
                        ),
                )

            importDataUseCase.importSelected(
                backup = backup,
                selectedInstanceIndices = emptySet(),
                selectedDownloadClientIndices = emptySet(),
                selectedCustomWebpageIndices = setOf(0),
                importTabPreferences = false,
                importUiPreferences = false,
            )

            assertEquals(2, webpages.value.size)
            assertEquals("https://local.example.com", webpages.value.first { it.id == 5L }.url)
            assertEquals(
                "https://backup.example.com",
                webpages.value.first { it.id != 5L }.url,
            )
        }
}
