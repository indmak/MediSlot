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
        return createStaff(phone, rawPassword, name, Role.PATIENT);
    }

    /**
     * 创建内部账号（管理员 / 医生 / 知识库维护员），供管理后台使用。
     */
    @Transactional
    public User createStaff(String phone, String rawPassword, String name, Role role) {
        validatePassword(rawPassword);
        if (userRepository.existsByPhone(phone)) {
            throw new BusinessException("该手机号已存在");
        }
        User user = new User(phone, passwordEncoder.encode(rawPassword), name, role);
        return userRepository.save(user);
    }

    /** 密码策略：至少 8 位，且同时包含字母与数字。 */
    public static void validatePassword(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 8
                || !rawPassword.matches(".*[A-Za-z].*")
                || !rawPassword.matches(".*\\d.*")) {
            throw new BusinessException("密码至少 8 位，且需同时包含字母和数字");
        }
    }

    /** 保存两步验证相关字段。 */
    @Transactional
    public void updateTotp(User user) {
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User findByPhone(String phone) {
        return userRepository.findByPhone(phone)
                .orElseThrow(() -> new BusinessException("用户不存在：" + phone));
    }

    /** 管理后台账号列表（管理员 + 知识库维护员）。 */
    @Transactional(readOnly = true)
    public List<User> listStaff() {
        return userRepository.findAllByRoleInOrderByCreatedAtDesc(List.of(Role.ADMIN, Role.KB_MAINTAINER));
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
