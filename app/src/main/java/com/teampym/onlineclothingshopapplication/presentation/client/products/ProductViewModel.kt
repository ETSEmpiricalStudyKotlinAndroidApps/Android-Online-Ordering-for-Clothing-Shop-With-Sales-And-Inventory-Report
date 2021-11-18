package com.teampym.onlineclothingshopapplication.presentation.client.products

import androidx.lifecycle.* // ktlint-disable no-wildcard-imports
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.teampym.onlineclothingshopapplication.data.room.PreferencesManager
import com.teampym.onlineclothingshopapplication.data.room.SortOrder
import com.teampym.onlineclothingshopapplication.data.repository.ProductRepositoryImpl
import com.teampym.onlineclothingshopapplication.data.util.PRODUCTS_COLLECTION
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductViewModel @Inject constructor(
    private val db: FirebaseFirestore,
    private val productRepository: ProductRepositoryImpl,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    val searchQuery = MutableLiveData("")
    private val _categoryQuery = MutableLiveData("")

    var productsFlow = combine(
        searchQuery.asFlow(),
        preferencesManager.preferencesFlow
    ) { search, sessionPref ->
        Pair(search, sessionPref)
    }.flatMapLatest { (search, sessionPref) ->

        var queryProducts: Query?
        val categoryId = _categoryQuery.value

        queryProducts = when (sessionPref.sortOrder) {
            SortOrder.BY_NAME -> {
                if (search.isEmpty()) {
                    db.collection(PRODUCTS_COLLECTION)
                        .whereEqualTo("categoryId", categoryId)
                        .orderBy("name", Query.Direction.ASCENDING)
                        .limit(30)
                } else {
                    db.collection(PRODUCTS_COLLECTION)
                        .whereEqualTo("categoryId", categoryId)
                        .orderBy("name", Query.Direction.ASCENDING)
                        .startAt(search)
                        .endAt(search + '\uf8ff')
                        .limit(30)
                }
            }
            SortOrder.BY_NEWEST -> {
                if (search.isEmpty()) {
                    db.collection(PRODUCTS_COLLECTION)
                        .whereEqualTo("categoryId", categoryId)
                        .orderBy("dateAdded", Query.Direction.DESCENDING)
                        .limit(30)
                } else {
                    db.collection(PRODUCTS_COLLECTION)
                        .whereEqualTo("categoryId", categoryId)
                        .orderBy("dateAdded", Query.Direction.DESCENDING)
                        .startAt(search)
                        .endAt(search + '\uf8ff')
                        .limit(30)
                }
            }
            SortOrder.BY_POPULARITY -> {
                if (search.isEmpty()) {
                    db.collection(PRODUCTS_COLLECTION)
                        .whereEqualTo("categoryId", categoryId)
                        .orderBy("name", Query.Direction.ASCENDING)
                        .limit(30)
                } else {
                    db.collection(PRODUCTS_COLLECTION)
                        .whereEqualTo("categoryId", categoryId)
                        .orderBy("name", Query.Direction.ASCENDING)
                        .startAt(search)
                        .endAt(search + '\uf8ff')
                        .limit(30)
                }
            }
        }

        productRepository.getSome(queryProducts, sessionPref.sortOrder).flow
    }

    fun updateCategory(categoryId: String) {
        _categoryQuery.value = categoryId
    }

    fun updateSortOrder(sortOrder: SortOrder) = viewModelScope.launch {
        preferencesManager.updateSortOrder(sortOrder)
    }
}
