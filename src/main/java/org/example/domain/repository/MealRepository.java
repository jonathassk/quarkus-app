package org.example.domain.repository;

import io.quarkus.hibernate.orm.panache.PanacheRepository;
import org.example.domain.entity.Meal;

public class MealRepository implements PanacheRepository<Meal> {}