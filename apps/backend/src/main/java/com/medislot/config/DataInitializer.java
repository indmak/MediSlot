package com.medislot.config;

import com.medislot.entity.AppSetting;
import com.medislot.entity.Department;
import com.medislot.entity.Doctor;
import com.medislot.entity.Role;
import com.medislot.entity.Schedule;
import com.medislot.entity.User;
import com.medislot.repository.AppSettingRepository;
import com.medislot.repository.DepartmentRepository;
import com.medislot.repository.DoctorRepository;
import com.medislot.repository.ScheduleRepository;
import com.medislot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 启动初始化：
 * <ul>
 *   <li>始终确保存在一个内置管理员账号（幂等）；</li>
 *   <li>当 {@code medislot.seed.enabled=true} 且库为空时，写入演示数据：
 *       5 个科室、5 位不同科室的医生、1 位患者，以及未来 7 天排班。</li>
 * </ul>
 */
@Configuration
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;
    private final ScheduleRepository scheduleRepository;
    private final AppSettingRepository appSettingRepository;
    private final PasswordEncoder passwordEncoder;

    private final boolean seedEnabled;
    private final String adminPhone;
    private final String adminPassword;

    public DataInitializer(UserRepository userRepository,
                           DepartmentRepository departmentRepository,
                           DoctorRepository doctorRepository,
                           ScheduleRepository scheduleRepository,
                           AppSettingRepository appSettingRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${medislot.seed.enabled:false}") boolean seedEnabled,
                           @Value("${medislot.admin.phone:13000000000}") String adminPhone,
                           @Value("${medislot.admin.password:admin123}") String adminPassword) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.doctorRepository = doctorRepository;
        this.scheduleRepository = scheduleRepository;
        this.appSettingRepository = appSettingRepository;
        this.passwordEncoder = passwordEncoder;
        this.seedEnabled = seedEnabled;
        this.adminPhone = adminPhone;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(String... args) {
        ensureAdmin();
        ensureSettings();
        if (seedEnabled) {
            seedDemoData();
        } else {
            log.info("[init] 演示数据未启用（medislot.seed.enabled=false）");
        }
    }

    /** 幂等写入系统设置默认值（管理员设置中心）。 */
    private void ensureSettings() {
        Map<String, String[]> defaults = new LinkedHashMap<>();
        defaults.put("consultation.enabled", new String[]{"true", "诊前咨询功能开关"});
        defaults.put("consultation.max-messages", new String[]{"30", "单个咨询会话消息上限"});
        defaults.put("consultation.rate-limit-seconds", new String[]{"3", "患者发送消息的最小间隔（秒）"});
        defaults.put("consultation.max-history", new String[]{"20", "发送给 AI 的最近历史消息条数"});
        defaults.forEach((key, value) -> {
            if (appSettingRepository.findById(key).isEmpty()) {
                appSettingRepository.save(new AppSetting(key, value[0], value[1]));
            }
        });
    }

    /** 幂等地保证存在一个管理员账号（生产环境同样内置）。 */
    private void ensureAdmin() {
        if (userRepository.countByRole(Role.ADMIN) > 0) {
            log.info("[init] 管理员账号已存在，跳过");
            return;
        }
        userRepository.save(new User(adminPhone, encode(adminPassword), "系统管理员", Role.ADMIN));
        log.info("[init] 已内置管理员账号：{}", adminPhone);
    }

    /** 演示数据：5 个科室 + 5 位不同科室的医生 + 1 位患者 + 未来 7 天排班。 */
    private void seedDemoData() {
        if (departmentRepository.count() > 0) {
            log.info("[seed] 已有数据，跳过演示数据初始化");
            return;
        }
        log.info("[seed] 写入演示数据……");

        Department internal = departmentRepository.save(new Department("内科", 1));
        Department surgery = departmentRepository.save(new Department("外科", 2));
        Department pediatrics = departmentRepository.save(new Department("儿科", 3));
        Department orthopedics = departmentRepository.save(new Department("骨科", 4));
        Department ophthalmology = departmentRepository.save(new Department("眼科", 5));

        // 患者
        userRepository.save(new User("13900000000", encode("patient123"), "王小明", Role.PATIENT));

        // 5 位医生，分布在 5 个不同科室（各自挂号费不同）
        createDoctor("13800000001", "doctor123", "张明华", "副主任医师",
                internal, "从事内科临床工作 15 年，擅长心血管与呼吸系统常见病。", new BigDecimal("30.00"));
        createDoctor("13800000002", "doctor123", "李建国", "主任医师",
                surgery, "普外科主任，擅长微创手术与术后康复管理。", new BigDecimal("50.00"));
        createDoctor("13800000003", "doctor123", "王丽", "主治医师",
                pediatrics, "儿科门诊医生，关注儿童常见病与生长发育。", new BigDecimal("20.00"));
        createDoctor("13800000004", "doctor123", "赵强", "副主任医师",
                orthopedics, "骨科专家，擅长骨关节与运动损伤诊治。", new BigDecimal("30.00"));
        createDoctor("13800000005", "doctor123", "陈静", "主治医师",
                ophthalmology, "眼科医生，擅长近视防控与干眼症治疗。", new BigDecimal("20.00"));

        seedSchedules(LocalDate.now(), 7);
        log.info("[seed] 完成：5 个科室 / 5 位医生 / 1 位患者。医生 13800000001/doctor123，患者 13900000000/patient123");
    }

    private void createDoctor(String phone, String rawPassword, String name, String title,
                              Department department, String bio, BigDecimal registrationFee) {
        User user = userRepository.save(new User(phone, encode(rawPassword), name, Role.DOCTOR));
        Doctor doctor = new Doctor(user, department, title, bio);
        doctor.setRating(BigDecimal.valueOf(4.7 + Math.random() * 0.2).setScale(1, RoundingMode.HALF_UP));
        doctor.setAppointmentCount(100 + (int) (Math.random() * 400));
        doctor.setRegistrationFee(registrationFee);
        doctorRepository.save(doctor);
    }

    private void seedSchedules(LocalDate from, int days) {
        List<LocalTime[]> slots = List.of(
                new LocalTime[]{LocalTime.of(9, 0), LocalTime.of(9, 30)},
                new LocalTime[]{LocalTime.of(9, 30), LocalTime.of(10, 0)},
                new LocalTime[]{LocalTime.of(10, 0), LocalTime.of(10, 30)},
                new LocalTime[]{LocalTime.of(14, 0), LocalTime.of(14, 30)},
                new LocalTime[]{LocalTime.of(14, 30), LocalTime.of(15, 0)}
        );
        for (Doctor doctor : doctorRepository.findAll()) {
            for (int d = 0; d < days; d++) {
                LocalDate date = from.plusDays(d);
                for (LocalTime[] slot : slots) {
                    scheduleRepository.save(new Schedule(doctor, date, slot[0], slot[1], 3));
                }
            }
        }
    }

    private String encode(String raw) {
        return passwordEncoder.encode(raw);
    }
}
