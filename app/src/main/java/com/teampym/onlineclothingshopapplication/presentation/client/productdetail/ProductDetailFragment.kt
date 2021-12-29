package com.teampym.onlineclothingshopapplication.presentation.client.productdetail

import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import com.teampym.onlineclothingshopapplication.R
import com.teampym.onlineclothingshopapplication.data.util.UserType
import com.teampym.onlineclothingshopapplication.databinding.FragmentProductDetailBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject

@AndroidEntryPoint
class ProductDetailFragment : Fragment(R.layout.fragment_product_detail) {

    private lateinit var binding: FragmentProductDetailBinding

    private val args by navArgs<ProductDetailFragmentArgs>()

    private val viewModel: ProductDetailViewModel by viewModels()

    @Inject
    lateinit var db: FirebaseFirestore

    private lateinit var adapter: ReviewAdapter

    private var myMenu: Menu? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentProductDetailBinding.bind(view)
        adapter = ReviewAdapter()

        var product = args.product
        val productId = args.productId

        if (product == null) {
            // Product ID Should not be null if Product Parcelable is null
            viewModel.getProductById(productId!!)
        } else {
            viewModel.updateProduct(product)
        }

        viewModel.product.observe(viewLifecycleOwner) {
            product = it
        }

        binding.apply {
            btnAddToCart.setOnClickListener {
                val action =
                    ProductDetailFragmentDirections.actionProductDetailFragmentToInventoryModalFragment(
                        product!!
                    )
                findNavController().navigate(action)
            }

            val priceStr = "$" + product?.price
            tvProductName.text = product?.name
            tvPrice.text = priceStr
            val descStr = if (product!!.description.isBlank()) {
                "No Available Description"
            } else {
                product?.description
            }

            tvDescription.text = descStr
            btnAddToCart.setOnClickListener {
                val action =
                    ProductDetailFragmentDirections.actionProductDetailFragmentToInventoryModalFragment(
                        product!!
                    )
                findNavController().navigate(action)
            }

            // submit list to the image adapter
            val viewPager = carouselViewPager.apply {
                adapter = ImagePagerAdapter(requireActivity()).apply {
                    submitList(product?.productImageList)
                    notifyDataSetChanged()
                }
            }

            // Attach viewPager and the tabLayout together so that they can work altogether
            TabLayoutMediator(indicatorTabLayout, viewPager) { _, _ -> }.attach()

            // submit list to the adapter if the reviewList is not empty.
            adapter.submitList(product!!.reviewList)

            var rate = 0.0
            if (product!!.totalRate > 0.0 && product!!.numberOfReviews > 0L) {
                rate = product!!.avgRate.toDouble()
            }

            val rateStr = "- $rate"
            tvRate.text = rateStr
            ratingBar.rating = rate.toFloat()

            if (product!!.numberOfReviews > 5L) {
                tvShowMoreReviews.text =
                    getString(R.string.label_show_more_reviews, product!!.numberOfReviews)
            } else {
                tvShowMoreReviews.isVisible = false
            }

            if (rate == 0.0) {
                labelNoReviews.isVisible = true
            } else {

                // Load Reviews here.
                recyclerReviews.setHasFixedSize(true)
                recyclerReviews.adapter = adapter
            }
        }

        setHasOptionsMenu(true)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.product_detail_action_menu, menu)

        myMenu = menu

        viewModel.getUserSession().observe(viewLifecycleOwner) { session ->
            when (session.userType) {
                UserType.CUSTOMER.name -> {
                    myMenu?.let {
                        it.findItem(R.id.action_cart).isVisible = true
                    }
                }
                UserType.ADMIN.name -> {
                    myMenu?.let {
                        it.findItem(R.id.action_add_edit_stock).isVisible = true
                        binding.btnAddToCart.isVisible = false
                    }
                }
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_share -> {
                // TODO("Create copy link")
                true
            }
            R.id.action_cart -> {
                findNavController().navigate(R.id.action_global_cartFragment)
                true
            }
            R.id.action_edit_product -> {
                // Navigate to add/edit product layout when admin
                val action = ProductDetailFragmentDirections
                    .actionProductDetailFragmentToAddEditProductFragment(
                        "Edit Product (${viewModel.product.value!!.productId})",
                        viewModel.product.value,
                        true,
                        viewModel.product.value!!.categoryId
                    )
                findNavController().navigate(action)
                true
            }
            R.id.action_add_new_inventory -> {
                // Navigate to add inventory layout when admin
                val action = ProductDetailFragmentDirections
                    .actionProductDetailFragmentToAddInventoryFragment(
                        viewModel.product.value!!.productId
                    )
                findNavController().navigate(action)
                true
            }
            R.id.action_stock_in -> {
                // TODO("Navigate to stock in layout when admin")
                true
            }
            else -> false
        }
    }
}
