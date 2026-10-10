package com.tqfapp.android.ui.home

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.tqfapp.android.R
import com.tqfapp.android.databinding.ActivityHomeBinding
import com.tqfapp.android.ui.auth.LoginActivity
import com.tqfapp.android.ui.chat.ChatFragment
import com.tqfapp.android.ui.contracts.ContractsFragment
import com.tqfapp.android.ui.profile.ProfileFragment

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var isProviderUser: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        isProviderUser = intent.getBooleanExtra(LoginActivity.EXTRA_IS_PROVIDER, false)

        if (savedInstanceState == null) {
            if (isProviderUser) {
                // Prestador de serviço entra diretamente no seu próprio perfil de gerenciamento
                replaceFragment(ProfessionalProfileFragment.newInstance(isOwner = true))
            } else {
                // Contratante entra na tela Home de busca
                replaceFragment(HomeFragment())
            }
        }

        setupBottomNavigation()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    replaceFragment(HomeFragment())
                    true
                }
                R.id.nav_contracts -> {
                    replaceFragment(ContractsFragment())
                    true
                }
                R.id.nav_chat -> {
                    replaceFragment(ChatFragment())
                    true
                }
                R.id.nav_profile -> {
                    if (isProviderUser) {
                        // Perfil do Prestador (Modo Dono / Edição)
                        replaceFragment(ProfessionalProfileFragment.newInstance(isOwner = true))
                    } else {
                        // Perfil do Contratante ("Ana Lima")
                        replaceFragment(ProfileFragment())
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}