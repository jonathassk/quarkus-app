package org.example.infrastructure.config.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.example.application.services.TokenService;
import org.example.application.services.UserService;
import org.example.application.services.impl.UserServiceImpl;
import org.example.application.usecases.CreateTripUseCaseimpl;
import org.example.application.usecases.CreateUserUseCaseImpl;
import org.example.application.usecases.LoginUserUseCaseImpl;
import org.example.application.usecases.interfaces.CreateTripUseCase;
import org.example.application.usecases.interfaces.CreateUserUseCase;
import org.example.application.usecases.interfaces.LoginUserUseCase;
import org.example.controller.TripController;
import org.example.controller.UserController;
import org.example.domain.repository.*;
import org.example.utils.UserDataVerification;

@ApplicationScoped
public class ApplicationConfig {

    @ConfigProperty(name = "mp.jwt.verify.issuer")
    String issuer;

    @ConfigProperty(name = "mp.jwt.sign.key.location")
    String privateKeyLocation;
    
    @Produces
    @ApplicationScoped
    public UserDataVerification userDataVerification() {
        return new UserDataVerification();
    }

    @Produces
    @ApplicationScoped
    public UserService userValidationService(
            UserRepository userRepository,
            TokenService tokenService) {
        return new UserServiceImpl(userRepository, tokenService);
    }

    @Produces
    @ApplicationScoped
    public UserRepository userRepository() {
        return new UserRepository();
    }

    @Produces
    @ApplicationScoped
    public TripRepository tripRepository() { return new TripRepository(); }

    @Produces
    @ApplicationScoped
    public TripSegmentRepository tripSegmentRepository() { return new TripSegmentRepository(); }

    @Produces
    @ApplicationScoped
    public ActivityRepository activityRepository() { return new ActivityRepository(); }

    @Produces
    @ApplicationScoped
    public MealRepository mealRepository() { return new MealRepository(); }

    @Produces
    @ApplicationScoped
    public TripUserRepository tripUserRepository() { return new TripUserRepository(); }

    @Produces
    @ApplicationScoped
    public CreateUserUseCase createUserUseCase(
            UserService userService,
            UserRepository userRepository) {
        return new CreateUserUseCaseImpl(userService, userRepository);
    }

    @Produces
    @ApplicationScoped
    public CreateTripUseCase createTripUseCase(TripRepository tripRepository, UserRepository userRepository) {
        return new CreateTripUseCaseimpl(tripRepository, userRepository);
    }

    @Produces
    @ApplicationScoped
    public LoginUserUseCase loginUserUseCase(
            UserService userService,
            UserRepository userRepository) {
        return new LoginUserUseCaseImpl(userService, userRepository);
    }

    @Produces
    @ApplicationScoped
    public TripController tripController(CreateTripUseCase createTripUseCase, UserRepository userRepository, TripRepository tripRepository) {
        return new TripController(createTripUseCase, userRepository, tripRepository);
    }

    @Produces
    @ApplicationScoped
    public UserController userController(
            UserDataVerification userDataVerification,
            CreateUserUseCase createUserUseCase,
            LoginUserUseCase loginUserUseCase) {
        return new UserController(userDataVerification, createUserUseCase, loginUserUseCase);
    }


} 