package org.example.domain.repository;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import org.example.domain.entity.Activity;

public class ActivityRepository implements PanacheRepositoryBase<Activity, UUID> {}