package com.suptech.postventa.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
public class UsuariosConfig {

    @Bean
    PasswordEncoder codificadorClaves() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    UserDetailsService usuarios(SeguridadProperties propiedades, PasswordEncoder codificador) {
        UserDetails agente = User.withUsername(propiedades.agente().usuario())
                .password(codificador.encode(propiedades.agente().clave()))
                .roles("AGENTE")
                .build();

        UserDetails administrador = User.withUsername(propiedades.admin().usuario())
                .password(codificador.encode(propiedades.admin().clave()))
                .roles("ADMIN", "AGENTE")
                .build();

        return new InMemoryUserDetailsManager(agente, administrador);
    }
}
