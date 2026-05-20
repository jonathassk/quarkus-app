# Exemplo de Uso - Objeto Trip Completo

Este documento demonstra como criar e usar um objeto `Trip` completo com todas as suas entidades relacionadas.

## 📋 Especificações do Exemplo

O exemplo criado contém:

- **3 dias** de viagem (15, 16 e 17 de abril de 2024)
- **4 atividades** por dia (total de 12 atividades)
- **3 refeições** por dia (total de 9 refeições)
- **2 usuários** com diferentes níveis de permissão:
  - João Silva (OWNER)
  - Maria Santos (ADMIN)

## 📁 Arquivos Criados

### 1. `exemplo-trip-completo.json`
Arquivo JSON com a estrutura completa do objeto Trip. Pode ser usado para:
- Testes de API
- Importação de dados
- Documentação da estrutura

### 2. `ExemploTripCompleto.java`
Classe Java demonstrando como criar o objeto programaticamente usando:
- Builder Pattern (Lombok)
- Relacionamentos JPA
- Estrutura hierárquica completa

## 🚀 Como Usar

### Opção 1: Usando o JSON
```bash
# Para testar via API REST
curl -X POST http://localhost:8080/api/v1/trips/create-trip \
  -H "Content-Type: application/json" \
  -d @exemplo-trip-completo.json
```

### Opção 2: Usando o Java
```java
// Importar a classe de exemplo
import org.example.examples.ExemploTripCompleto;

// Criar o objeto Trip completo
Trip trip = ExemploTripCompleto.criarExemploTripCompleto();

// Usar em testes ou persistir no banco
tripRepository.persist(trip);
```

### Opção 3: Executar o exemplo
```bash
# Compilar e executar
./mvnw compile
java -cp target/classes org.example.examples.ExemploTripCompleto
```

## 📊 Estrutura do Exemplo

### Viagem Principal
```json
{
  "name": "Viagem para Paris - Primavera 2024",
  "budgetTotal": 8500.00,
  "startDate": "2024-04-15",
  "endDate": "2024-04-17",
  "visibility": "private"
}
```

### Usuários
- **João Silva** (OWNER): Criador da viagem, permissões totais
- **Maria Santos** (ADMIN): Pode editar, mas não deletar a viagem

### Atividades por Dia
**Dia 1 (15/04):**
- Museu do Louvre (CULTURAL)
- Passeio de Barco pelo Rio Sena (TURISMO)
- Torre Eiffel (TURISMO)
- Arco do Triunfo (CULTURAL)

**Dia 2 (16/04):**
- Museu d'Orsay (CULTURAL)
- Jardim de Luxemburgo (LAZER)
- Catedral de Notre-Dame (CULTURAL)
- Tour Montmartre (TURISMO)

**Dia 3 (17/04):**
- Palácio de Versalhes (CULTURAL)
- Galeries Lafayette (SHOPPING)
- Champs-Élysées (TURISMO)
- Centro Pompidou (CULTURAL)

### Refeições por Dia
Cada dia inclui:
- **BREAKFAST**: Café da manhã no hotel
- **LUNCH**: Almoço em restaurante local
- **DINNER**: Jantar em restaurante gastronômico

## 🔧 Níveis de Permissão

O sistema suporta três níveis de permissão:

| Nível | Descrição | Pode Editar | Pode Gerenciar Usuários | Pode Deletar |
|-------|-----------|-------------|-------------------------|--------------|
| **OWNER** | Proprietário | ✅ | ✅ | ✅ |
| **ADMIN** | Administrador | ✅ | ❌ | ❌ |
| **VIEWER** | Visualizador | ❌ | ❌ | ❌ |

## 💡 Dicas de Uso

### Para Desenvolvimento
1. Use o JSON para testar endpoints da API
2. Use o Java para testes unitários
3. Modifique os dados conforme necessário

### Para Testes
```java
@Test
public void testCriarTripCompleto() {
    Trip trip = ExemploTripCompleto.criarExemploTripCompleto();
    
    // Verificar estrutura
    assertEquals(3, trip.getSegments().size());
    assertEquals(12, trip.getSegments().stream()
        .mapToInt(s -> s.getActivities().size()).sum());
    assertEquals(9, trip.getSegments().stream()
        .mapToInt(s -> s.getMeals().size()).sum());
    assertEquals(2, trip.getUsers().size());
}
```

### Para Produção
- Ajuste os dados conforme sua necessidade
- Valide os relacionamentos antes de persistir
- Considere usar transações para operações complexas

## 🛠️ Personalização

Para criar seu próprio exemplo:

1. **Modificar datas**: Altere `LocalDate.of(2024, 4, 15)` para suas datas
2. **Adicionar usuários**: Crie novos objetos `User` e `TripUser`
3. **Criar atividades**: Use diferentes `activityType` (CULTURAL, TURISMO, LAZER, SHOPPING)
4. **Definir refeições**: Use `mealType` (BREAKFAST, LUNCH, DINNER)
5. **Ajustar orçamento**: Modifique `budgetTotal` conforme necessário

## 📝 Observações

- Todos os custos estão em euros (€)
- Endereços são reais de Paris
- Sites são URLs reais dos estabelecimentos
- O exemplo usa relacionamentos bidirecionais JPA
- Todos os objetos usam Builder Pattern do Lombok 