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
import org.example.controller.EmailPreferencesController;
import org.example.controller.TripController;
import org.example.controller.TripChecklistController;
import org.example.controller.TripDocumentController;
import org.example.controller.TripShareController;
import org.example.controller.ChatController;
import org.example.controller.ChatPrivacyController;
import org.example.controller.TripChatController;
import org.example.controller.EventController;
import org.example.controller.EventChatController;
import org.example.controller.EventPostController;
import org.example.controller.UserController;
import org.example.application.services.AuthSessionService;
import org.example.application.services.email.EmailPreferencesService;
import org.example.application.services.impl.UserSyncService;
import org.example.application.services.TripCollaborationService;
import org.example.infrastructure.storage.ObjectStorageService;
import org.example.infrastructure.auth.NeonAuthJwtVerifier;
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
            TripService tripService,
            org.example.application.services.chat.TripChatService tripChatService) {
        return new UpdateTripUseCaseImpl(tripRepository, userRepository, tripService, tripChatService);
    }

    @Produces
    @ApplicationScoped
    public CreateTripUseCase createTripUseCase(
            TripRepository tripRepository,
            UserRepository userRepository,
            TripService tripService,
            ActivityRepository activityRepository,
            MealRepository mealRepository,
            org.example.application.services.chat.TripChatService tripChatService,
            AgencyMemberRepository agencyMemberRepository) {
        return new CreateTripUseCaseimpl(
                tripRepository,
                userRepository,
                tripService,
                activityRepository,
                mealRepository,
                tripChatService,
                agencyMemberRepository);
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
            TokenService tokenService,
            ObjectStorageService objectStorageService) {
        return new UserController(
                userDataVerification, createUserUseCase, loginUserUseCase, userRepository, tokenService, objectStorageService);
    }

    @Produces
    @ApplicationScoped
    public AuthController authController(
            TokenService tokenService,
            UserRepository userRepository,
            AuthSessionService authSessionService,
            MagicLinkService magicLinkService,
            NeonAuthJwtVerifier neonAuthJwtVerifier) {
        return new AuthController(
                tokenService, userRepository, authSessionService, magicLinkService, neonAuthJwtVerifier);
    }

    @Produces
    @ApplicationScoped
    public EmailPreferencesController emailPreferencesController(
            TokenService tokenService,
            UserRepository userRepository,
            EmailPreferencesService emailPreferencesService) {
        return new EmailPreferencesController(tokenService, userRepository, emailPreferencesService);
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

    @Produces
    @ApplicationScoped
    public ChatController chatController(
            TokenService tokenService,
            UserRepository userRepository,
            org.example.application.services.chat.InboxService inboxService,
            org.example.application.services.chat.MessageService messageService,
            org.example.application.services.chat.DirectChatService directChatService,
            org.example.application.services.chat.ChatWsTokenService chatWsTokenService) {
        return new ChatController(
                tokenService, userRepository, inboxService, messageService, directChatService, chatWsTokenService);
    }

    @Produces
    @ApplicationScoped
    public TripChatController tripChatController(
            TokenService tokenService,
            UserRepository userRepository,
            org.example.application.services.chat.TripChatService tripChatService) {
        return new TripChatController(tokenService, userRepository, tripChatService);
    }

    @Produces
    @ApplicationScoped
    public ChatPrivacyController chatPrivacyController(
            TokenService tokenService,
            UserRepository userRepository,
            org.example.application.services.chat.DirectChatService directChatService,
            org.example.application.services.chat.PrivacyService privacyService) {
        return new ChatPrivacyController(tokenService, userRepository, directChatService, privacyService);
    }

    @Produces
    @ApplicationScoped
    public EventController eventController(
            TokenService tokenService,
            UserRepository userRepository,
            org.example.application.services.event.EventService eventService,
            org.example.application.services.event.EventParticipantService participantService) {
        return new EventController(tokenService, userRepository, eventService, participantService);
    }

    @Produces
    @ApplicationScoped
    public EventPostController eventPostController(
            TokenService tokenService,
            UserRepository userRepository,
            org.example.application.services.event.EventPostService eventPostService) {
        return new EventPostController(tokenService, userRepository, eventPostService);
    }

    @Produces
    @ApplicationScoped
    public EventChatController eventChatController(
            TokenService tokenService,
            UserRepository userRepository,
            org.example.application.services.event.EventChatService eventChatService) {
        return new EventChatController(tokenService, userRepository, eventChatService);
    }
} 