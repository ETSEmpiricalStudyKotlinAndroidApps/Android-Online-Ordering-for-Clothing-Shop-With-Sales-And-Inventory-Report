package com.teampym.onlineclothingshopapplication.presentation.client.inventories

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.firebase.auth.FirebaseAuth
import com.teampym.onlineclothingshopapplication.R
import com.teampym.onlineclothingshopapplication.data.room.Inventory
import com.teampym.onlineclothingshopapplication.data.room.Product
import com.teampym.onlineclothingshopapplication.databinding.FragmentInventoryModalBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class InventoryModalFragment : BottomSheetDialogFragment() {

    private lateinit var binding: FragmentInventoryModalBinding

    private val viewModel: InventoryViewModel by viewModels()

    private val args by navArgs<InventoryModalFragmentArgs>()

    private lateinit var product: Product

    private var selectedInv: Inventory? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_inventory_modal, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentInventoryModalBinding.bind(view)

        val currentUser = FirebaseAuth.getInstance().currentUser
        var userId = ""
        if (currentUser != null) {
            userId = currentUser.uid
        }

        product = args.product

        binding.apply {
            Glide.with(requireView())
                .load(product.imageUrl)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .error(R.drawable.ic_food)
                .into(imgProductInventory)

            tvProductName.text = product.name

            binding.btnAddToCart.setOnClickListener {
                viewModel.addToCart(
                    userId,
                    product,
                    selectedInv!!
                )
                Toast.makeText(requireContext(), "Added ${product.name} to cart.", Toast.LENGTH_SHORT).show()
                findNavController().popBackStack()
            }
        }

        if (product.inventoryList.size == 1) {
            viewModel.addToCart(userId, product, product.inventoryList[0])
            findNavController().popBackStack()
        }

        if (product.inventoryList.isNotEmpty()) {
            for (inventory in product.inventoryList) {
                val chip = layoutInflater.inflate(R.layout.inventory_item, null, false) as Chip
                chip.id = View.generateViewId()
                chip.text = inventory.size
                chip.isCheckable = true
                chip.isEnabled = inventory.stock > 0
                chip.setOnClickListener {
                    binding.btnAddToCart.isEnabled =
                        binding.chipSizeGroup.checkedChipIds.count() == 1 && userId.isNotEmpty()
                    selectedInv = inventory

                    val numberOfAvailableStocksForSize = "(Available: ${selectedInv?.stock ?: 0})"
                    binding.tvAvailableStocks.text = numberOfAvailableStocksForSize
                }
                binding.chipSizeGroup.addView(chip)
            }
            binding.chipSizeGroup.isSingleSelection = true
        }
    }
}
