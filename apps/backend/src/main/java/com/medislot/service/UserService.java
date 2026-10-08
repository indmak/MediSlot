package com.medislot.service;

import com.medislot.entity.Role;
import com.medislot.entity.User;
import com.medislot.exception.BusinessException;
import com.medislot.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户业务：注册、按手机号查询、Spring Security 登录认证。
 */
@Service
public class UserService implements UserDetailsService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 患者注册。
     */
    @Transactional
    public User register(String phone, String rawPassword, String name) {
        if (userRepository.existsByPhone(phone)) {
            throw new BusinessException("该手机号已注册");
        }
        User user = new User(phone, passwordEncoder.encode(rawPassword), name, Role.PATIENT);
        return userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User findByPhone(String phone) {
        return userRepository.findByPhone(phone)
                .orElseThrow(() -> new BusinessException("用户不存在：" + phone));
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String phone) throws UsernameNotFoundException {
        User user = userRepository.findByPhone(phone)
                .orElseThrow(() -> new UsernameNotFoundException("手机号未注册：" + phone));
        return new org.springframework.security.core.userdetails.User(
                user.getPhone(),
                user.getPassword(),
                List.of(new SimpleGrantedAuthority(user.getRole().authority()))
        );
    }
}
