package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import org.example.domain.entity.TripUser;

public class TripUserRepository implements PanacheRepository<TripUser> {}
