package org.example.examples;

import org.example.domain.entity.*;
import org.example.domain.enums.UserPermissionLevel;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Exemplo de como criar um objeto Trip completo com:
 * - 3 dias de viagem
 * - 4 atividades por dia
 * - 3 refeições por dia
 * - 2 usuários (1 OWNER, 1 ADMIN)
 */
public class ExemploTripCompleto {

    public static Trip criarExemploTripCompleto() {
        // Criar usuários
        User joao = User.builder()
                .id(1L)
                .fullName("João Silva")
                .email("joao.silva@email.com")
                .username("joao_silva")
                .passwordHash("$2a$10$hashedPassword")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        User maria = User.builder()
                .id(2L)
                .fullName("Maria Santos")
                .email("maria.santos@email.com")
                .username("maria_santos")
                .passwordHash("$2a$10$hashedPassword")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Criar Workspace
        Workspace workspace = Workspace.builder()
                .name("Workspace Pessoal de João Silva")
                .planType("FREE")
                .primaryColor("#000000")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Criar Trip
        Trip trip = Trip.builder()
                .name("Viagem para Paris - Primavera 2024")
                .description("Uma viagem incrível para a cidade luz durante a primavera, explorando museus, gastronomia e cultura francesa.")
                .budgetTotal(new BigDecimal("8500.00"))
                .startDate(LocalDate.of(2024, 4, 15))
                .endDate(LocalDate.of(2024, 4, 17))
                .durationDays(3)
                .targetMonth(4)
                .coverImageUrl("https://example.com/images/paris-spring-2024.jpg")
                .visibility("private")
                .createdBy(joao)
                .workspace(workspace)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // Criar TripUsers (relacionamentos usuário-viagem)
        TripUser tripUserJoao = TripUser.builder()
                .user(joao)
                .trip(trip)
                .permissionLevel("OWNER")
                .build();

        TripUser tripUserMaria = TripUser.builder()
                .user(maria)
                .trip(trip)
                .permissionLevel("ADMIN")
                .build();

        // Criar segmentos (3 dias)
        List<TripSegment> segments = Arrays.asList(
                criarSegmentoDia1(trip),
                criarSegmentoDia2(trip),
                criarSegmentoDia3(trip)
        );

        // Configurar relacionamentos
        trip.setUsers(Arrays.asList(tripUserJoao, tripUserMaria));
        trip.setSegments(segments);

        return trip;
    }

    private static TripSegment criarSegmentoDia1(Trip trip) {
        TripSegment segment = TripSegment.builder()
                .cityId("paris-fr")
                .arrivalDate(LocalDate.of(2024, 4, 15))
                .departureDate(LocalDate.of(2024, 4, 15))
                .startDay(1)
                .endDay(1)
                .notes("Primeiro dia em Paris - chegada e exploração inicial da cidade")
                .trip(trip)
                .build();

        // Criar 4 atividades
        List<Activity> activities = Arrays.asList(
                Activity.builder()
                        .name("Visita ao Museu do Louvre")
                        .activityType("CULTURAL")
                        .address("Rue de Rivoli, 75001 Paris, França")
                        .cost(new BigDecimal("17.00"))
                        .site("https://www.louvre.fr")
                        .date(LocalDate.of(2024, 4, 15))
                        .dayNumber(1)
                        .segment(segment)
                        .build(),
                Activity.builder()
                        .name("Passeio de Barco pelo Rio Sena")
                        .activityType("TURISMO")
                        .address("Port de la Bourdonnais, 75007 Paris, França")
                        .cost(new BigDecimal("15.00"))
                        .site("https://www.bateauxparisiens.com")
                        .date(LocalDate.of(2024, 4, 15))
                        .dayNumber(1)
                        .segment(segment)
                        .build(),
                Activity.builder()
                        .name("Subida na Torre Eiffel")
                        .activityType("TURISMO")
                        .address("Champ de Mars, 5 Avenue Anatole France, 75007 Paris, França")
                        .cost(new BigDecimal("26.10"))
                        .site("https://www.toureiffel.paris")
                        .date(LocalDate.of(2024, 4, 15))
                        .dayNumber(1)
                        .segment(segment)
                        .build(),
                Activity.builder()
                        .name("Visita ao Arco do Triunfo")
                        .activityType("CULTURAL")
                        .address("Place Charles de Gaulle, 75008 Paris, França")
                        .cost(new BigDecimal("13.00"))
                        .site("https://www.paris-arc-de-triomphe.fr")
                        .date(LocalDate.of(2024, 4, 15))
                        .dayNumber(1)
                        .segment(segment)
                        .build()
        );

        // Criar 3 refeições
        List<Meal> meals = Arrays.asList(
                Meal.builder()
                        .mealType("BREAKFAST")
                        .name("Café da manhã no hotel")
                        .description("Café continental com croissants e café")
                        .location("Hotel Le Marais")
                        .address("Rue de Rivoli, 75004 Paris, França")
                        .cost(new BigDecimal("0.00"))
                        .date(LocalDate.of(2024, 4, 15))
                        .dayNumber(1)
                        .segment(segment)
                        .build(),
                Meal.builder()
                        .mealType("LUNCH")
                        .name("Almoço no Le Comptoir du Relais")
                        .description("Cozinha francesa tradicional - confit de pato com batatas")
                        .location("Le Comptoir du Relais")
                        .address("9 Carrefour de l'Odéon, 75006 Paris, França")
                        .cost(new BigDecimal("45.00"))
                        .date(LocalDate.of(2024, 4, 15))
                        .dayNumber(1)
                        .segment(segment)
                        .build(),
                Meal.builder()
                        .mealType("DINNER")
                        .name("Jantar no L'Ami Louis")
                        .description("Restaurante tradicional francês - especialidade em frango assado")
                        .location("L'Ami Louis")
                        .address("32 Rue du Vertbois, 75003 Paris, França")
                        .cost(new BigDecimal("85.00"))
                        .date(LocalDate.of(2024, 4, 15))
                        .dayNumber(1)
                        .segment(segment)
                        .build()
        );

        segment.setActivities(activities);
        segment.setMeals(meals);

        return segment;
    }

    private static TripSegment criarSegmentoDia2(Trip trip) {
        TripSegment segment = TripSegment.builder()
                .cityId("paris-fr")
                .arrivalDate(LocalDate.of(2024, 4, 16))
                .departureDate(LocalDate.of(2024, 4, 16))
                .startDay(2)
                .endDay(2)
                .notes("Segundo dia - foco em arte e gastronomia")
                .trip(trip)
                .build();

        // Criar 4 atividades
        List<Activity> activities = Arrays.asList(
                Activity.builder()
                        .name("Visita ao Museu d'Orsay")
                        .activityType("CULTURAL")
                        .address("1 Rue de la Légion d'Honneur, 75007 Paris, França")
                        .cost(new BigDecimal("16.00"))
                        .site("https://www.musee-orsay.fr")
                        .date(LocalDate.of(2024, 4, 16))
                        .dayNumber(2)
                        .segment(segment)
                        .build(),
                Activity.builder()
                        .name("Passeio pelo Jardim de Luxemburgo")
                        .activityType("LAZER")
                        .address("Rue de Medicis, 75006 Paris, França")
                        .cost(new BigDecimal("0.00"))
                        .site("https://www.senat.fr/visite/jardin")
                        .date(LocalDate.of(2024, 4, 16))
                        .dayNumber(2)
                        .segment(segment)
                        .build(),
                Activity.builder()
                        .name("Visita à Catedral de Notre-Dame")
                        .activityType("CULTURAL")
                        .address("6 Parvis Notre-Dame - Pl. Jean-Paul II, 75004 Paris, França")
                        .cost(new BigDecimal("0.00"))
                        .site("https://www.notredamedeparis.fr")
                        .date(LocalDate.of(2024, 4, 16))
                        .dayNumber(2)
                        .segment(segment)
                        .build(),
                Activity.builder()
                        .name("Tour pelo Bairro de Montmartre")
                        .activityType("TURISMO")
                        .address("Montmartre, 75018 Paris, França")
                        .cost(new BigDecimal("0.00"))
                        .site("https://www.parisinfo.com/montmartre")
                        .date(LocalDate.of(2024, 4, 16))
                        .dayNumber(2)
                        .segment(segment)
                        .build()
        );

        // Criar 3 refeições
        List<Meal> meals = Arrays.asList(
                Meal.builder()
                        .mealType("BREAKFAST")
                        .name("Café da manhã no hotel")
                        .description("Café continental com pães frescos")
                        .location("Hotel Le Marais")
                        .address("Rue de Rivoli, 75004 Paris, França")
                        .cost(new BigDecimal("0.00"))
                        .date(LocalDate.of(2024, 4, 16))
                        .dayNumber(2)
                        .segment(segment)
                        .build(),
                Meal.builder()
                        .mealType("LUNCH")
                        .name("Almoço no Frenchie")
                        .description("Cozinha francesa moderna - menu degustação")
                        .location("Frenchie")
                        .address("5 Rue du Nil, 75002 Paris, França")
                        .cost(new BigDecimal("65.00"))
                        .date(LocalDate.of(2024, 4, 16))
                        .dayNumber(2)
                        .segment(segment)
                        .build(),
                Meal.builder()
                        .mealType("DINNER")
                        .name("Jantar no Le Chateaubriand")
                        .description("Restaurante gastronômico - menu surpresa do chef")
                        .location("Le Chateaubriand")
                        .address("129 Avenue Parmentier, 75011 Paris, França")
                        .cost(new BigDecimal("95.00"))
                        .date(LocalDate.of(2024, 4, 16))
                        .dayNumber(2)
                        .segment(segment)
                        .build()
        );

        segment.setActivities(activities);
        segment.setMeals(meals);

        return segment;
    }

    private static TripSegment criarSegmentoDia3(Trip trip) {
        TripSegment segment = TripSegment.builder()
                .cityId("paris-fr")
                .arrivalDate(LocalDate.of(2024, 4, 17))
                .departureDate(LocalDate.of(2024, 4, 17))
                .startDay(3)
                .endDay(3)
                .notes("Último dia - compras e despedida de Paris")
                .trip(trip)
                .build();

        // Criar 4 atividades
        List<Activity> activities = Arrays.asList(
                Activity.builder()
                        .name("Visita ao Palácio de Versalhes")
                        .activityType("CULTURAL")
                        .address("Place d'Armes, 78000 Versailles, França")
                        .cost(new BigDecimal("20.00"))
                        .site("https://en.chateauversailles.fr")
                        .date(LocalDate.of(2024, 4, 17))
                        .dayNumber(3)
                        .segment(segment)
                        .build(),
                Activity.builder()
                        .name("Compras na Galeries Lafayette")
                        .activityType("SHOPPING")
                        .address("40 Boulevard Haussmann, 75009 Paris, França")
                        .cost(new BigDecimal("0.00"))
                        .site("https://www.galerieslafayette.com")
                        .date(LocalDate.of(2024, 4, 17))
                        .dayNumber(3)
                        .segment(segment)
                        .build(),
                Activity.builder()
                        .name("Passeio pela Champs-Élysées")
                        .activityType("TURISMO")
                        .address("Avenue des Champs-Élysées, 75008 Paris, França")
                        .cost(new BigDecimal("0.00"))
                        .site("https://www.parisinfo.com/champs-elysees")
                        .date(LocalDate.of(2024, 4, 17))
                        .dayNumber(3)
                        .segment(segment)
                        .build(),
                Activity.builder()
                        .name("Visita ao Centro Pompidou")
                        .activityType("CULTURAL")
                        .address("Place Georges-Pompidou, 75004 Paris, França")
                        .cost(new BigDecimal("14.00"))
                        .site("https://www.centrepompidou.fr")
                        .date(LocalDate.of(2024, 4, 17))
                        .dayNumber(3)
                        .segment(segment)
                        .build()
        );

        // Criar 3 refeições
        List<Meal> meals = Arrays.asList(
                Meal.builder()
                        .mealType("BREAKFAST")
                        .name("Café da manhã no hotel")
                        .description("Café continental com frutas frescas")
                        .location("Hotel Le Marais")
                        .address("Rue de Rivoli, 75004 Paris, França")
                        .cost(new BigDecimal("0.00"))
                        .date(LocalDate.of(2024, 4, 17))
                        .dayNumber(3)
                        .segment(segment)
                        .build(),
                Meal.builder()
                        .mealType("LUNCH")
                        .name("Almoço no Septime")
                        .description("Cozinha francesa contemporânea - menu sazonal")
                        .location("Septime")
                        .address("80 Rue de Charonne, 75011 Paris, França")
                        .cost(new BigDecimal("55.00"))
                        .date(LocalDate.of(2024, 4, 17))
                        .dayNumber(3)
                        .segment(segment)
                        .build(),
                Meal.builder()
                        .mealType("DINNER")
                        .name("Jantar de despedida no Le Jules Verne")
                        .description("Restaurante na Torre Eiffel - vista panorâmica de Paris")
                        .location("Le Jules Verne")
                        .address("Avenue Gustave Eiffel, 75007 Paris, França")
                        .cost(new BigDecimal("120.00"))
                        .date(LocalDate.of(2024, 4, 17))
                        .dayNumber(3)
                        .segment(segment)
                        .build()
        );

        segment.setActivities(activities);
        segment.setMeals(meals);

        return segment;
    }

    /**
     * Método para demonstrar como usar o exemplo
     */
    public static void main(String[] args) {
        Trip trip = criarExemploTripCompleto();
        
        System.out.println("=== EXEMPLO TRIP COMPLETO ===");
        System.out.println("Nome da viagem: " + trip.getName());
        System.out.println("Orçamento total: R$ " + trip.getBudgetTotal());
        System.out.println("Período: " + trip.getStartDate() + " a " + trip.getEndDate());
        System.out.println("Criado por: " + trip.getCreatedBy().getFullName());
        
        System.out.println("\n=== USUÁRIOS ===");
        trip.getUsers().forEach(tripUser -> {
            System.out.println("- " + tripUser.getUser().getFullName() + 
                             " (" + tripUser.getPermissionLevel() + ")");
        });
        
        System.out.println("\n=== SEGMENTOS ===");
        trip.getSegments().forEach(segment -> {
            System.out.println("Dia " + segment.getArrivalDate() + ": " + segment.getNotes());
            System.out.println("  Atividades: " + segment.getActivities().size());
            System.out.println("  Refeições: " + segment.getMeals().size());
        });
        
        System.out.println("\n=== RESUMO ===");
        System.out.println("Total de dias: " + trip.getSegments().size());
        System.out.println("Total de atividades: " + trip.getSegments().stream()
                .mapToInt(s -> s.getActivities().size()).sum());
        System.out.println("Total de refeições: " + trip.getSegments().stream()
                .mapToInt(s -> s.getMeals().size()).sum());
        System.out.println("Total de usuários: " + trip.getUsers().size());
    }
} 