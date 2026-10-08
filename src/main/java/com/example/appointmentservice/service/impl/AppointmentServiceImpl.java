package com.example.appointmentservice.service.impl;

import com.example.appointmentservice.entity.Appointment;
import com.example.appointmentservice.repository.AppointmentRepository;
import com.example.appointmentservice.service.AppointmentService;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AppointmentServiceImpl implements AppointmentService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentServiceImpl.class);

    private final AppointmentRepository appointmentRepository;
    private final RestTemplate restTemplate;

    @Autowired
    public AppointmentServiceImpl(AppointmentRepository appointmentRepository, RestTemplate restTemplate) {
        this.appointmentRepository = appointmentRepository;
        this.restTemplate = restTemplate;
    }

    @Override
    @Retry(name = "patientRetry", fallbackMethod = "patientFallback")
    public Object verifyPatient(Long patientId) {
        log.info("Calling Patient-Service to verify patient ID: {}", patientId);
        String patientUrl = "http://patient-service/api/v1/patients/" + patientId;
        ResponseEntity<Object> response = restTemplate.getForEntity(patientUrl, Object.class);
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalArgumentException("Patient with ID " + patientId + " does not exist.");
        }
        return response.getBody();
    }

    public Object patientFallback(Long patientId, Throwable throwable) {
        log.error("Fallback triggered for patient ID: {}. Error: {}", patientId, throwable.getMessage());
        throw new IllegalStateException("Patient-Service is currently unavailable. Please try again later. (Error: " + throwable.getMessage() + ")", throwable);
    }

    @Override
    public Appointment createAppointment(Appointment appointment) {
        if (appointment.getPatientId() == null) {
            throw new IllegalArgumentException("Patient ID must not be null");
        }
        if (appointment.getDoctorId() == null) {
            throw new IllegalArgumentException("Doctor ID must not be null");
        }

        // Verify patient existence via Patient-Service with @Retry
        verifyPatient(appointment.getPatientId());

        // Verify doctor existence via Doctor-Service on Eureka
        try {
            String doctorUrl = "http://doctor-service/api/v1/doctors/" + appointment.getDoctorId();
            ResponseEntity<Object> response = restTemplate.getForEntity(doctorUrl, Object.class);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new IllegalArgumentException("Doctor with ID " + appointment.getDoctorId() + " does not exist.");
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Doctor with ID " + appointment.getDoctorId() + " not found or Doctor-Service unavailable: " + e.getMessage());
        }

        if (appointment.getAppointmentDate() == null) {
            appointment.setAppointmentDate(LocalDateTime.now());
        }

        if (appointment.getStatus() == null || appointment.getStatus().trim().isEmpty()) {
            appointment.setStatus("PENDING");
        }

        return appointmentRepository.save(appointment);
    }

    @Override
    public List<Appointment> getAllAppointments() {
        return appointmentRepository.findAll();
    }

    @Override
    public Appointment getAppointmentById(Long id) {
        return appointmentRepository.findById(id).orElse(null);
    }
}
