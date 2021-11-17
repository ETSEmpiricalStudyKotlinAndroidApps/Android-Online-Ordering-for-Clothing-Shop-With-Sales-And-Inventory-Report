package com.teampym.onlineclothingshopapplication.presentation.client.deliveryinformation

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.snackbar.Snackbar
import com.teampym.onlineclothingshopapplication.R
import com.teampym.onlineclothingshopapplication.data.models.DeliveryInformation
import com.teampym.onlineclothingshopapplication.databinding.FragmentDeliveryInformationBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest

@AndroidEntryPoint
class DeliveryInformationFragment :
    Fragment(R.layout.fragment_delivery_information),
    DeliveryInformationAdapter.OnDeliveryInformationListener {

    private lateinit var binding: FragmentDeliveryInformationBinding

    private val viewModel by viewModels<DeliveryInformationViewModel>()

    private lateinit var adapter: DeliveryInformationAdapter

    private var defaultDeliveryInfo: DeliveryInformation? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding = FragmentDeliveryInformationBinding.bind(view)

        adapter = DeliveryInformationAdapter(this)

        viewModel.deliveryInformation.observe(viewLifecycleOwner) { deliveryInfoList ->
            if (deliveryInfoList.isNotEmpty()) {
                val filteredList = deliveryInfoList.filter { !it.isPrimary }
                adapter.submitList(filteredList)
                binding.recyclerDeliveryInformation.setHasFixedSize(true)
                binding.recyclerDeliveryInformation.adapter = adapter

                binding.tvNoAddressYet.visibility = View.GONE

                defaultDeliveryInfo = deliveryInfoList.firstOrNull { it.isPrimary }
                if (defaultDeliveryInfo != null) {
                    binding.apply {
                        viewSelectedDeliveryInfo.visibility = View.VISIBLE

                        val contact = if (defaultDeliveryInfo?.contactNo?.get(0).toString() == "0")
                            defaultDeliveryInfo?.contactNo?.length?.let {
                                defaultDeliveryInfo?.contactNo?.substring(
                                    1,
                                    it
                                )
                            } else defaultDeliveryInfo?.contactNo

                        val nameAndContact = "${defaultDeliveryInfo?.name} | (+63) $contact"
                        tvNameAndContact.text = nameAndContact

                        val completeAddress = "${defaultDeliveryInfo?.streetNumber} " +
                            "${defaultDeliveryInfo?.city}, " +
                            "${defaultDeliveryInfo?.province}, " +
                            "${defaultDeliveryInfo?.province}, " +
                            defaultDeliveryInfo?.postalCode
                        tvAddress.text = completeAddress
                    }
                }
            }
        }

        binding.apply {
            fabCreateNew.setOnClickListener {
                val action =
                    DeliveryInformationFragmentDirections.actionDeliveryInformationFragmentToAddEditDeliveryInformationFragment(
                        null,
                        "Add New Address"
                    )
                findNavController().navigate(action)
            }
        }

        lifecycleScope.launchWhenStarted {
            viewModel.deliveryInfoEvent.collectLatest { event ->
                when (event) {
                    is DeliveryInformationViewModel.DeliveryInfoEvent.ShowMessage -> {
                        Snackbar.make(requireView(), event.msg, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onMakeDefaultClicked(deliveryInfo: DeliveryInformation) {
        AlertDialog.Builder(requireContext())
            .setTitle("Set Default Delivery Information")
            .setMessage(
                "Are you sure you want to make this address your default delivery address?\n" +
                    "Reminder: You cannot reverse this action"
            )
            .setPositiveButton("Yes") { dialog, _ ->
                viewModel.onDeliveryInformationDefaultChanged(
                    defaultDeliveryInfo?.copy(isPrimary = false),
                    deliveryInfo.copy(isPrimary = true)
                )
                dialog.dismiss()
            }.setNegativeButton("No") { dialog, _ ->
                dialog.dismiss()
            }.show()
    }

    override fun onEditClicked(deliveryInfo: DeliveryInformation) {
        val action =
            DeliveryInformationFragmentDirections.actionDeliveryInformationFragmentToAddEditDeliveryInformationFragment(
                deliveryInfo,
                "Edit Address"
            )
        findNavController().navigate(action)
    }
}