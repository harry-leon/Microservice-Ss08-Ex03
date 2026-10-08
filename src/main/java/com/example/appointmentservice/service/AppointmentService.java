package com.example.appointmentservice.service;

import com.example.appointmentservice.entity.Appointment;

import java.util.List;

public interface AppointmentService {
    Appointment createAppointment(Appointment appointment);
    Object verifyPatient(Long patientId);
    List<Appointment> getAllAppointments();
    Appointment getAppointmentById(Long id);
}
