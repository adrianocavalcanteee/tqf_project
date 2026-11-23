package com.tqfapp.android.ui.home

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.tqfapp.android.R
import com.tqfapp.android.data.repository.UserRepository
import com.tqfapp.android.databinding.ActivityHomeBinding
import com.tqfapp.android.ui.auth.LoginActivity
import com.tqfapp.android.ui.contracts.ContractsFragment
import com.tqfapp.android.ui.profile.ProfileFragment
import com.tqfapp.android.utils.UserSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.tqfapp.android.ui.common.applySystemBarsPadding

class HomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityHomeBinding
    private var isProviderUser: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            binding = ActivityHomeBinding.inflate(layoutInflater)
            setContentView(binding.root)
            binding.root.applySystemBarsPadding()

            // Tipo da conta já resolvido no login (usuarios.is_provider), sem nova ida à rede aqui.
            isProviderUser = intent.getBooleanExtra(
                LoginActivity.EXTRA_IS_PROVIDER,
                UserSessionManager.isProviderUser(this)
            )

            if (savedInstanceState == null) {
                if (isProviderUser) {
                    replaceFragment(ProviderHomeFragment())
                } else {
                    replaceFragment(HomeFragment())
                }
            }

            setupBottomNavigation()
            selectNavItem(binding.btnNavHome)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun setupBottomNavigation() {
        binding.btnNavHome.setOnClickListener {
            selectNavItem(binding.btnNavHome)
            if (isProviderUser) {
                replaceFragment(ProviderHomeFragment())
            } else {
                replaceFragment(HomeFragment())
            }
        }

        binding.btnNavContracts.setOnClickListener {
            selectNavItem(binding.btnNavContracts)
            replaceFragment(ContractsFragment())
        }

        binding.btnNavProfile.setOnClickListener {
            selectNavItem(binding.btnNavProfile)
            // As telas de perfil leem o usuário logado várias vezes: carrega uma vez em IO (fica em
            // cache no UserRepository) antes de abrir, para não travar a thread principal.
            binding.btnNavProfile.isEnabled = false
            lifecycleScope.launch {
                withContext(Dispatchers.IO) { UserRepository(applicationContext).getCurrentUser() }
                binding.btnNavProfile.isEnabled = true
                if (isProviderUser) {
                    replaceFragment(ProfessionalProfileFragment.newInstance(isOwner = true))
                } else {
                    replaceFragment(ProfileFragment())
                }
            }
        }
    }

    /** Abre a aba Perfil (toque na foto do usuário nas Homes); mesmo caminho do item da barra. */
    fun showProfileTab() {
        binding.btnNavProfile.performClick()
    }

    /** Abre a aba Contratações (atalho do card-resumo das Homes), com o item da barra destacado. */
    fun showContractsTab() {
        selectNavItem(binding.btnNavContracts)
        replaceFragment(ContractsFragment())
    }

    /** Destaque visual do item ativo da barra inferior (só aparência; a navegação segue igual). */
    private fun selectNavItem(item: View) {
        listOf(binding.btnNavHome, binding.btnNavContracts, binding.btnNavProfile).forEach { it.isSelected = it == item }
    }

    private fun replaceFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
