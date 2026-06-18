package org.example.infrastructure.config.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.example.application.services.MagicLinkService;
import org.example.application.services.TokenService;
import org.example.application.services.TripService;
import org.example.application.services.UserService;
import org.example.application.services.B2bAuditService;
import org.example.application.services.impl.TripServiceImpl;
import org.example.application.services.impl.UserServiceImpl;
import org.example.application.usecases.CreateTripUseCaseimpl;
import org.example.application.usecases.CreateUserUseCaseImpl;
import org.example.application.usecases.LoginUserUseCaseImpl;
import org.example.application.usecases.UpdateTripUseCaseImpl;
import org.example.application.usecases.interfaces.CreateTripUseCase;
import org.example.application.usecases.interfaces.CreateUserUseCase;
import org.example.application.usecases.interfaces.LoginUserUseCase;
import org.example.application.usecases.interfaces.UpdateTripUseCase;
import org.example.controller.AuthController;
import org.example.controller.TripController;
import org.example.controller.TripChecklistController;
import org.example.controller.TripDocumentController;
import org.example.controller.TripShareController;
import org.example.controller.UserController;
import org.example.application.services.AuthSessionService;
import org.example.application.services.impl.UserSyncService;
import org.example.application.services.TripCollaborationService;
import org.example.infrastructure.storage.ObjectStorageService;
import org.example.domain.repository.*;
import org.example.utils.UserDataVerification;
@ApplicationScoped
public class ApplicationConfig {

    @Produces
    @ApplicationScoped
    public UserDataVerification userDataVerification() {
        return new UserDataVerification();
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
    public TripDocumentRepository tripDocumentRepository() { return new TripDocumentRepository(); }

    @Produces
    @ApplicationScoped
    public TripChecklistItemRepository tripChecklistItemRepository() {
        return new TripChecklistItemRepository();
    }



    @Produces
    @ApplicationScoped
    public TripService tripService(TripSegmentRepository tripSegmentRepository) {
        return new TripServiceImpl(tripSegmentRepository);
    }

    @Produces
    @ApplicationScoped
    public UserService userService(UserRepository userRepository, TokenService tokenService) {
        return new UserServiceImpl(userRepository, tokenService);
    }

    @Produces
    @ApplicationScoped
    public CreateUserUseCase createUserUseCase(
            UserService userService,
            UserRepository userRepository,
            UserSyncService userSyncService) {
        return new CreateUserUseCaseImpl(userService, userRepository, userSyncService);
    }

    @Produces
    @ApplicationScoped
    public UpdateTripUseCase updateTripUseCase(
            TripRepository tripRepository,
            UserRepository userRepository,
            TripService tripService) {
        return new UpdateTripUseCaseImpl(tripRepository, userRepository, tripService);
    }

    @Produces
    @ApplicationScoped
    public CreateTripUseCase createTripUseCase(
            TripRepository tripRepository,
            UserRepository userRepository,
            TripService tripService,
            ActivityRepository activityRepository,
            MealRepository mealRepository) {
        return new CreateTripUseCaseimpl(tripRepository, userRepository, tripService, activityRepository, mealRepository);
    }

    @Produces
    @ApplicationScoped
    public LoginUserUseCase loginUserUseCase(
            UserService userService,
            UserRepository userRepository,
            TokenService tokenService) {
        return new LoginUserUseCaseImpl(userService, userRepository, tokenService);
    }

    @Produces
    @ApplicationScoped
    public TripController tripController(
            CreateTripUseCase createTripUseCase,
            UpdateTripUseCase updateTripUseCase,
            UserRepository userRepository,
            TripRepository tripRepository,
            TokenService tokenService,
            org.example.application.services.TripCollaborationService tripCollaborationService,
            AgencyMemberRepository agencyMemberRepository,
            org.example.application.services.B2bAuditService auditService) {
        return new TripController(
                createTripUseCase,
                updateTripUseCase,
                userRepository,
                tripRepository,
                tokenService,
                tripCollaborationService,
                agencyMemberRepository,
                auditService);
    }

    @Produces
    @ApplicationScoped
    public UserController userController(
            UserDataVerification userDataVerification,
            CreateUserUseCase createUserUseCase,
            LoginUserUseCase loginUserUseCase,
            UserRepository userRepository,
            TokenService tokenService) {
        return new UserController(
                userDataVerification, createUserUseCase, loginUserUseCase, userRepository, tokenService);
    }

    @Produces
    @ApplicationScoped
    public AuthController authController(
            TokenService tokenService,
            UserRepository userRepository,
            AuthSessionService authSessionService,
            MagicLinkService magicLinkService) {
        return new AuthController(
                tokenService, userRepository, authSessionService, magicLinkService);
    }

    @Produces
    @ApplicationScoped
    public TripDocumentController tripDocumentController(
            TripRepository tripRepository,
            TripDocumentRepository tripDocumentRepository,
            UserRepository userRepository,
            TokenService tokenService,
            ObjectStorageService objectStorageService,
            B2bAuditService auditService) {
        return new TripDocumentController(
                tripRepository,
                tripDocumentRepository,
                userRepository,
                tokenService,
                objectStorageService,
                auditService);
    }

    @Produces
    @ApplicationScoped
    public TripChecklistController tripChecklistController(
            TripRepository tripRepository,
            TripChecklistItemRepository tripChecklistItemRepository,
            UserRepository userRepository,
            TokenService tokenService,
            B2bAuditService auditService) {
        return new TripChecklistController(
                tripRepository, tripChecklistItemRepository, userRepository, tokenService, auditService);
    }

    @Produces
    @ApplicationScoped
    public TripShareController tripShareController(
            TripRepository tripRepository,
            UserRepository userRepository,
            TokenService tokenService,
            TripCollaborationService tripCollaborationService,
            B2bAuditService auditService) {
        return new TripShareController(
                tripRepository, userRepository, tokenService, tripCollaborationService, auditService);
    }
} 