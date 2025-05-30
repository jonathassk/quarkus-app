package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import org.example.domain.entity.Activity;

public class ActivityRepository implements PanacheRepository<Activity> {}