package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import org.example.domain.entity.Trip;

public class TripRepository implements PanacheRepository<Trip> {}
