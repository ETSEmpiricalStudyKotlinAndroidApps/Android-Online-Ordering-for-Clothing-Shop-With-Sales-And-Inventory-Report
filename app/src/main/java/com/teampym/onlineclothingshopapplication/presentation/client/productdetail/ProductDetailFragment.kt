package com.teampym.onlineclothingshopapplication.presentation.client.productdetail

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.firebase.firestore.FirebaseFirestore
import com.teampym.onlineclothingshopapplication.R
import com.teampym.onlineclothingshopapplication.databinding.FragmentProductDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class ProductDetailFragment : Fragment(R.layout.fragment_product_detail) {

    private lateinit var binding: FragmentProductDetailBinding

    private val args by navArgs<ProductDetailFragmentArgs>()

    private val viewModel: ProductDetailViewModel by viewModels()

    @Inject
    lateinit var db: FirebaseFirestore

    private lateinit var adapter: ReviewAdapter

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentProductDetailBinding.bind(view)

        var product = args.product
        val productId = args.productId

        if (product == null) {
            product = viewModel.getProductById(productId!!)
        }

        adapter = ReviewAdapter()

        binding.apply {
            btnAddToCart.setOnClickListener {
                val action =
                    ProductDetailFragmentDirections.actionProductDetailFragmentToInventoryModalFragment(
                        product
                    )
                findNavController().navigate(action)
            }

            Glide.with(requireView())
                .load(product.imageUrl)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .error(R.drawable.ic_food)
                .into(imgProduct)

            tvProductName.text = product.name
            tvPrice.text = "$${product.price}"
            val descStr =
                if (product.description.isNotBlank()) product.description else "No Available Description."

            tvDescription.text = descStr
            btnAddToCart.setOnClickListener {
                val action =
                    ProductDetailFragmentDirections.actionProductDetailFragmentToInventoryModalFragment(
                        product
                    )
                findNavController().navigate(action)
            }

            // submit list to the adapter if the reviewList is not empty.
            product.let { p ->
                adapter.submitList(p.reviewList)

                val rate = p.avgRate

                if (rate == 0.0) {
                    labelRate.text = getString(R.string.no_available_rating)

                    tvRate.visibility = View.INVISIBLE
                    labelNoReviews.isVisible = true
                } else {
                    val rateStr = "- $rate"
                    tvRate.text = rateStr

                    // Load Reviews here.
                    recyclerReviews.setHasFixedSize(true)
                    recyclerReviews.adapter = adapter
                }
            }
        }
    }
}
