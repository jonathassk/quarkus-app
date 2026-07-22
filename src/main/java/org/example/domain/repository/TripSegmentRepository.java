package org.example.domain.repository;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import org.example.domain.entity.TripSegment;

public class TripSegmentRepository implements PanacheRepositoryBase<TripSegment, UUID> {
}
