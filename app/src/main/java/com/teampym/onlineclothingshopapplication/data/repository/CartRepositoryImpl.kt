package com.teampym.onlineclothingshopapplication.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.teampym.onlineclothingshopapplication.data.db.UserInformationDao
import com.teampym.onlineclothingshopapplication.data.models.Cart
import com.teampym.onlineclothingshopapplication.data.models.Inventory
import com.teampym.onlineclothingshopapplication.data.models.Product
import com.teampym.onlineclothingshopapplication.data.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class CartRepositoryImpl @Inject constructor(
    private val db: FirebaseFirestore,
    private val userInformationDao: UserInformationDao
) {

    private val userCartCollectionRef = db.collection(USERS_COLLECTION)
    private val updatedProductsCollectionRef = db.collection(PRODUCTS_COLLECTION)


    // TODO("Use a flow to get updates in cart collection instantly")
    fun getAll(userId: String): Flow<List<Cart>> = callbackFlow {
        val cartListener = userCartCollectionRef.document(userId)
            .collection(CART_SUB_COLLECTION)
            .addSnapshotListener { querySnapshot, firebaseException ->
                if (firebaseException != null) {
                    cancel(message = "Error fetching cart", firebaseException)
                    return@addSnapshotListener
                }

                val cartList = mutableListOf<Cart>()
                querySnapshot?.let { snapshot ->
                    for (document in snapshot.documents) {
                        val cartItem = document.toObject(Cart::class.java)!!.copy(id = document.id)

                        CoroutineScope(Dispatchers.IO).launch {
                            val productQuery =
                                updatedProductsCollectionRef.document(cartItem.product.id).get()
                                    .await()
                            if (productQuery.exists()) {
                                val updatePriceOfProductInCart = mapOf<String, Any>(
                                    "price" to productQuery["price"].toString().toBigDecimal()
                                )

                                userCartCollectionRef.document(userId)
                                    .collection(CART_SUB_COLLECTION)
                                    .document(document.id)
                                    .set(updatePriceOfProductInCart, SetOptions.merge()).await()
                            }
                        }
                        cartList.add(cartItem)
                    }
                }
                offer(cartList)
            }
        awaitClose {
            cartListener.remove()
        }
    }

    suspend fun insert(
        userId: String,
        product: Product,
        inventory: Inventory
    ): Resource {

        val cartQuery = userCartCollectionRef
            .document(userId)
            .collection("cart")
            .document(product.id)
            .get()
            .await()

        // check if the productId is existing in the user's cart. if true then update, if false then just add it.
        if (cartQuery.data != null) {
            val cartItem = cartQuery.toObject(Cart::class.java)!!.copy(id = cartQuery.id)

            cartItem.let {
                if (it.product.id == product.id && it.userId == userId) {
                    val updateQuantityOfItemInCart = mapOf<String, Any>(
                        "quantity" to it.quantity + 1
                    )

                    val result =
                        userCartCollectionRef.document(userId).collection(CART_SUB_COLLECTION)
                            .document(it.id).set(updateQuantityOfItemInCart, SetOptions.merge())
                            .await()
                    if (result != null) {
                        return Resource.Success("Success", cartItem)
                    }
                }
            }

        } else {
            val newItem = Cart(
                id = product.id,
                userId = userId,
                product = product,
                quantity = 1,
                sizeInv = inventory,
                subTotal = 0.0
            )
            newItem.subTotal = newItem.calculatedTotalPrice

            userCartCollectionRef.document(userId).collection(CART_SUB_COLLECTION)
                .document(product.id)
                .set(newItem)
                .addOnSuccessListener {
                    return@addOnSuccessListener
                }.addOnFailureListener {

                }
        }
        return Resource.Error("Failed", null)
    }

    suspend fun updateQty(
        userId: String,
        cartId: String,
        flag: String,
    ): Resource {
        val updateCartQtyQuery =
            userCartCollectionRef.document(userId).collection("cart").document(cartId).get().await()

        if (updateCartQtyQuery != null) {

            val cartItemToUpdate = updateCartQtyQuery.toObject(Cart::class.java)

            cartItemToUpdate?.let {
                val newQuantity = when (flag) {
                    CartFlag.ADDING.toString() -> it.quantity + 1
                    CartFlag.REMOVING.toString() -> it.quantity - 1
                    else -> 0L
                }

                val updateQuantityOfCartItem = mapOf<String, Any>(
                    "quantity" to newQuantity,
                    "subTotal" to (it.product!!.price * newQuantity.toDouble())
                )

                val result =
                    userCartCollectionRef.document(userId).collection("cart").document(cartId)
                        .set(updateQuantityOfCartItem, SetOptions.merge()).await()
                return Resource.Success("Success", result != null)

            }
        }
        return Resource.Error("Failed", false)
    }

    suspend fun delete(
        userId: String,
        cartId: String
    ): Resource {
        val cartItemQuery =
            userCartCollectionRef.document(userId).collection("cart").document(cartId).get().await()
        if (cartItemQuery != null) {
            val result =
                userCartCollectionRef.document(userId).collection("cart").document(cartId).delete()
                    .await()
            return Resource.Success("Success", result != null)
        }
        return Resource.Error("Failed", false)
    }

    suspend fun deleteAll(
        userId: String
    ): Resource {
        val cartQuery = userCartCollectionRef.document(userId).collection("cart").get().await()
        if (cartQuery != null) {
            for (cartItem in cartQuery.documents) {
                userCartCollectionRef.document(userId).collection("cart").document(cartItem.id)
                    .delete().await()
            }
            return Resource.Success("Success", emptyList<Cart>())
        }
        return Resource.Error("Failed", null)
    }
}