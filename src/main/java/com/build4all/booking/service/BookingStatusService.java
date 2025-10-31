package com.build4all.booking.service;

import com.build4all.booking.domain.BookingStatus;
import com.build4all.booking.repository.BookingStatusRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class BookingStatusService {

    private final BookingStatusRepository repo;

    public BookingStatusService(BookingStatusRepository repo) {
        this.repo = repo;
    }

    public List<BookingStatus> findAll() { return repo.findAll(); }
    public Optional<BookingStatus> findById(Long id) { return repo.findById(id); }
    public BookingStatus save(BookingStatus status) { return repo.save(status); }
    public void delete(Long id) { repo.deleteById(id); }
}
