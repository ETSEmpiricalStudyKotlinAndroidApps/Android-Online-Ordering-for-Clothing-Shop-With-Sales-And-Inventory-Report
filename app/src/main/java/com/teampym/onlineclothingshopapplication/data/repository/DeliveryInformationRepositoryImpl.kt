package com.teampym.onlineclothingshopapplication.data.repository

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import com.teampym.onlineclothingshopapplication.data.room.DeliveryInformation
import com.teampym.onlineclothingshopapplication.data.room.DeliveryInformationDao
import com.teampym.onlineclothingshopapplication.data.util.DELIVERY_INFORMATION_SUB_COLLECTION
import com.teampym.onlineclothingshopapplication.data.util.USERS_COLLECTION
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class DeliveryInformationRepositoryImpl @Inject constructor(
    db: FirebaseFirestore,
    val deliveryInformationDao: DeliveryInformationDao
) {

    private val userCollectionRef = db.collection(USERS_COLLECTION)

    suspend fun getAll(userId: String): List<DeliveryInformation> {
        val deliveryInformationQuery = userCollectionRef
            .document(userId)
            .collection(DELIVERY_INFORMATION_SUB_COLLECTION)
            .get()
            .await()

        val deliveryInformationList = mutableListOf<DeliveryInformation>()
        if (deliveryInformationQuery.documents.isNotEmpty()) {
            for (document in deliveryInformationQuery.documents) {
                val deliveryInformation = document
                    .toObject(DeliveryInformation::class.java)!!.copy(
                    id = document.id,
                    userId = userId,
                    isPrimary = document["isPrimary"].toString().toBoolean()
                )

                deliveryInformationList.add(deliveryInformation)
                deliveryInformationDao.insert(deliveryInformation)
            }
            return deliveryInformationList
        }
        return emptyList()
    }

    @ExperimentalCoroutinesApi
    fun getFlow(userId: String): Flow<List<DeliveryInformation>> = callbackFlow {
        val deliveryInformationListener = userCollectionRef
            .document(userId)
            .collection(DELIVERY_INFORMATION_SUB_COLLECTION)
            .addSnapshotListener { value, error ->
                if (error != null) {
                    cancel(message = "Error fetching deliveryInformation", error)
                    return@addSnapshotListener
                }

                val deliveryInformationList = mutableListOf<DeliveryInformation>()
                value?.let { querySnapshot ->
                    if (querySnapshot.documents.isNotEmpty()) {
                        for (document in querySnapshot.documents) {
                            val deliveryInformation = document
                                .toObject(DeliveryInformation::class.java)!!
                                .copy(
                                    id = document.id,
                                    userId = userId,
                                    isPrimary = document["isPrimary"].toString().toBoolean()
                                )

                            deliveryInformationList.add(deliveryInformation)
                            CoroutineScope(Dispatchers.IO).launch {
                                deliveryInformationDao.insert(deliveryInformation)
                            }
                        }
                        offer(deliveryInformationList)
                    }
                }
            }
        awaitClose {
            deliveryInformationListener.remove()
        }
    }

    suspend fun create(
        userId: String,
        deliveryInformation: DeliveryInformation?
    ): DeliveryInformation? {
        val createdDeliveryInfo = withContext(Dispatchers.IO) {
            var createdDeliveryInfoTemp = deliveryInformation
            createdDeliveryInfoTemp?.let { d ->
                userCollectionRef
                    .document(userId)
                    .collection(DELIVERY_INFORMATION_SUB_COLLECTION)
                    .add(d)
                    .addOnSuccessListener {
                        d.id = it.id
                    }.addOnFailureListener {
                        createdDeliveryInfoTemp = null
                        return@addOnFailureListener
                    }
            }
            return@withContext createdDeliveryInfoTemp
        }
        return createdDeliveryInfo
    }

    suspend fun update(
        userId: String,
        deliveryInformation: DeliveryInformation
    ): Boolean {
        var isSuccessful = true
        val isDeliveryInformationExistingQuery = userCollectionRef
            .document(userId)
            .collection(DELIVERY_INFORMATION_SUB_COLLECTION)
            .get()
            .await()

        isDeliveryInformationExistingQuery.documents.let { querySnapshot ->
            querySnapshot.forEach { doc ->
                val deliveryInformationFromDb = doc
                    .toObject(DeliveryInformation::class.java)!!.copy(
                    id = doc.id,
                    userId = userId,
                    isPrimary = doc["isPrimary"].toString().toBoolean()
                )

                if (deliveryInformation.id.isNotBlank() && deliveryInformation.id == deliveryInformationFromDb.id) {

                    val updateDeliveryInfoMap = mapOf<String, Any>(
                        "name" to deliveryInformation.name,
                        "contactNo" to deliveryInformation.contactNo,
                        "region" to deliveryInformation.region,
                        "province" to deliveryInformation.province,
                        "city" to deliveryInformation.city,
                        "streetNumber" to deliveryInformation.streetNumber,
                        "postalCode" to deliveryInformation.postalCode,
                        "isPrimary" to deliveryInformation.isPrimary
                    )

                    doc.reference.update(updateDeliveryInfoMap)
                        .addOnSuccessListener {
                        }.addOnFailureListener {
                            isSuccessful = false
                            return@addOnFailureListener
                        }
                }
            }
        }
        return isSuccessful
    }

    suspend fun changeDefault(
        userId: String,
        new: DeliveryInformation
    ): Boolean {
        val isSuccessful = withContext(Dispatchers.IO) {
            var isCompleted = true
            userCollectionRef.document(userId).collection(DELIVERY_INFORMATION_SUB_COLLECTION)
                .whereEqualTo("isPrimary", true)
                .limit(1)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    runBlocking {
                        isCompleted = switchDefaultAddressInFirestore(querySnapshot.documents[0], userId, new)
                    }
                }.addOnFailureListener {
                    isCompleted = false
                    return@addOnFailureListener
                }
            return@withContext isCompleted
        }
        return isSuccessful
    }

    private suspend fun switchDefaultAddressInFirestore(
        doc: DocumentSnapshot,
        userId: String,
        new: DeliveryInformation
    ): Boolean {
        val isSuccessful = withContext(Dispatchers.IO) {
            var isCompleted = true
            doc.let {
                it.reference.set(
                    mutableMapOf<String, Any>(
                        "isPrimary" to false
                    ),
                    SetOptions.merge()
                ).addOnSuccessListener {
                    val updateNewInfoMap = mapOf<String, Any>(
                        "isPrimary" to true
                    )

                    userCollectionRef
                        .document(userId)
                        .collection(DELIVERY_INFORMATION_SUB_COLLECTION)
                        .document(new.id)
                        .set(updateNewInfoMap, SetOptions.merge())
                        .addOnSuccessListener {
                        }.addOnFailureListener {
                            isCompleted = false
                            return@addOnFailureListener
                        }
                }.addOnFailureListener {
                    isCompleted = false
                    return@addOnFailureListener
                }
            }
            return@withContext isCompleted
        }
        return isSuccessful
    }

    suspend fun delete(userId: String, deliveryInformation: DeliveryInformation): Boolean {
        val isSuccessful = withContext(Dispatchers.IO) {
            var isCompleted = true
            userCollectionRef.document(userId)
                .collection(DELIVERY_INFORMATION_SUB_COLLECTION)
                .document(deliveryInformation.id)
                .delete()
                .addOnSuccessListener {
                }.addOnCanceledListener {
                    isCompleted = false
                    return@addOnCanceledListener
                }
            return@withContext isCompleted
        }
        return isSuccessful
    }
}
