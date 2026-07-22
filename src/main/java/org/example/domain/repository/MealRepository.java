package org.example.domain.repository;

import java.util.UUID;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;

import org.example.domain.entity.Meal;

public class MealRepository implements PanacheRepositoryBase<Meal, UUID> {}