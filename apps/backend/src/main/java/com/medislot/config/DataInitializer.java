package com.medislot.config;

import com.medislot.entity.Department;
import com.medislot.entity.Doctor;
import com.medislot.entity.Role;
import com.medislot.entity.Schedule;
import com.medislot.entity.User;
import com.medislot.repository.DepartmentRepository;
import com.medislot.repository.DoctorRepository;
import com.medislot.repository.ScheduleRepository;
import com.medislot.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * 本地开发演示数据：科室、账号、医生、未来 7 天排班。
 * 仅在 dev profile 且 medislot.seed.enabled=true 时执行，且只在库为空时写入一次。
 */
@Configuration
@ConditionalOnProperty(prefix = "medislot.seed", name = "enabled", havingValue = "true")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final DoctorRepository doctorRepository;
    private final ScheduleRepository scheduleRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UserRepository userRepository,
                           DepartmentRepository departmentRepository,
                           DoctorRepository doctorRepository,
                           ScheduleRepository scheduleRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.departmentRepository = departmentRepository;
        this.doctorRepository = doctorRepository;
        this.scheduleRepository = scheduleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (departmentRepository.count() > 0) {
            log.info("[seed] 已有数据，跳过初始化");
            return;
        }
        log.info("[seed] 写入演示数据……");

        Department internal = departmentRepository.save(new Department("内科", 1));
        Department surgery = departmentRepository.save(new Department("外科", 2));
        Department pediatrics = departmentRepository.save(new Department("儿科", 3));
        Department orthopedics = departmentRepository.save(new Department("骨科", 4));
        Department ophthalmology = departmentRepository.save(new Department("眼科", 5));

        // 管理员
        userRepository.save(new User("13000000000", encode("admin123"), "系统管理员", Role.ADMIN));
        // 患者
        userRepository.save(new User("13900000000", encode("patient123"), "王小明", Role.PATIENT));

        // 医生
        createDoctor("13800000001", "doctor123", "张明华", "副主任医师",
                internal, "从事内科临床工作 15 年，擅长心血管与呼吸系统常见病。");
        createDoctor("13800000002", "doctor123", "李建国", "主任医师",
                surgery, "普外科主任，擅长微创手术与术后康复管理。");
        createDoctor("13800000003", "doctor123", "王丽", "主治医师",
                pediatrics, "儿科门诊医生，关注儿童常见病与生长发育。");
        createDoctor("13800000004", "doctor123", "赵强", "副主任医师",
                orthopedics, "骨科专家，擅长骨关节与运动损伤诊治。");
        createDoctor("13800000005", "doctor123", "陈静", "主治医师",
                ophthalmology, "眼科医生，擅长近视防控与干眼症治疗。");

        seedSchedules(LocalDate.now(), 7);
        log.info("[seed] 完成。管理员 13000000000/admin123，医生 13800000001/doctor123，患者 13900000000/patient123");
    }

    private void createDoctor(String phone, String rawPassword, String name, String title,
                              Department department, String bio) {
        User user = userRepository.save(new User(phone, encode(rawPassword), name, Role.DOCTOR));
        Doctor doctor = new Doctor(user, department, title, bio);
        doctor.setRating(java.math.BigDecimal.valueOf(4.7 + Math.random() * 0.2).setScale(1, java.math.RoundingMode.HALF_UP));
        doctor.setAppointmentCount(100 + (int) (Math.random() * 400));
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
