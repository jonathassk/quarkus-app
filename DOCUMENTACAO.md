# Documentação do Projeto Quarkus App

## Visão Geral

Este projeto é uma aplicação backend desenvolvida com **Quarkus** (Java), focada em gerenciamento de viagens, usuários e permissões. Utiliza arquitetura limpa, separando domínio, aplicação, infraestrutura e interfaces REST, além de boas práticas de injeção de dependências, DTOs, mapeamento, criptografia e transações.

---

## Sumário

- [Tecnologias Utilizadas](#tecnologias-utilizadas)
- [Como Executar](#como-executar)
- [Estrutura do Projeto](#estrutura-do-projeto)
- [Principais Entidades](#principais-entidades)
- [DTOs (Data Transfer Objects)](#dtos-data-transfer-objects)
- [Endpoints REST](#endpoints-rest)
- [Segurança e Autenticação](#segurança-e-autenticação)
- [Configurações Importantes](#configurações-importantes)
- [Testes](#testes)
- [Dicas de Desenvolvimento](#dicas-de-desenvolvimento)

---

## Tecnologias Utilizadas

- **Quarkus 3.22.3**
- Java 21
- Hibernate ORM + Panache
- PostgreSQL (relacional) e MongoDB (NoSQL)
- ModelMapper (mapeamento DTO/Entidade)
- Lombok (boilerplate)
- JWT (autenticação)
- BCrypt (criptografia de senha)
- JUnit5, Mockito (testes)
- Docker/Docker Compose

---

## Como Executar

### Modo Dev

```sh
./mvnw compile quarkus:dev
```
Acesse o Dev UI: [http://localhost:8080/q/dev/](http://localhost:8080/q/dev/)

### Build e Execução

```sh
./mvnw package
java -jar target/quarkus-app/quarkus-run.jar
```

### Build Docker

```sh
docker build -t seu-usuario/quarkus-app:latest -f Dockerfile .
docker run -p 8080:8080 seu-usuario/quarkus-app:latest
```

### Native Build

```sh
./mvnw package -Dnative
```

---

## Estrutura do Projeto

```
src/
 └── main/
     ├── java/org/example/
     │   ├── controller/         # REST Controllers
     │   ├── application/        # Casos de uso, serviços, DTOs
     │   ├── domain/             # Entidades, repositórios, enums
     │   ├── infrastructure/     # Configurações, mapeadores, repositórios técnicos
     │   └── utils/              # Utilitários e validadores
     ├── resources/
     │   └── application.properties # Configurações do Quarkus
     └── docker/                 # Configuração Docker (se houver)
```

---

## Principais Entidades

- **User**: Usuário do sistema (id, nome, email, senha criptografada, etc).
- **Trip**: Viagem (id, nome, descrição, datas, orçamento, etc).
- **TripUser**: Relação usuário-viagem (permissão: OWNER, MEMBER, etc).
- **TripSegment**: Segmentos de uma viagem (cidade, datas, notas).
- **Activity**: Atividades em um segmento.
- **Meal**: Refeições em um segmento.

---

## DTOs (Data Transfer Objects)

### Usuário

- **UserCreateRequestDTO**: Dados para criação de usuário (nome, email, senha, etc).
- **UserLoginRequestDTO**: Dados para login (email, senha).
- **UserResponseDTO**: Dados retornados ao cliente.

### Viagem

- **TripRequestDTO**: Dados para criar/atualizar viagem.
- **TripResponseDTO**: Dados retornados de uma viagem.
- **TripSegmentDTO, ActivityDTO, MealDTO**: Dados de segmentos, atividades e refeições.

---

## Endpoints REST

### Usuários

- `POST /api/v1/users/create-user`  
  Cria um novo usuário.

- `POST /api/v1/users/login`  
  Realiza login e retorna dados do usuário.

- `GET /api/v1/users/test`  
  Endpoint de teste.

### Viagens

- `POST /api/v1/trips/create-trip`  
  Cria uma nova viagem.

- `GET /api/v1/trips/{tripId}`  
  Busca detalhes de uma viagem.

- `PUT /api/v1/trips/{tripId}/update-trip`  
  Atualiza dados completos da viagem.

- `PATCH /api/v1/trips/{tripId}/update-name-description`  
  Atualiza apenas nome e descrição.

- `PATCH /api/v1/trips/{tripId}/update-users-trip`  
  Atualiza usuários e permissões da viagem.

---

## Segurança e Autenticação

- **JWT**: Utilizado para autenticação e autorização.
- **BCrypt**: Senhas de usuários são criptografadas antes de persistir.
- **Configuração JWT**:  
  - Algoritmo: RS256  
  - Chaves configuradas em `application.properties`  
  - Token lifespan: 1 hora

---

## Configurações Importantes (`application.properties`)

- **Banco de Dados**:  
  - PostgreSQL:  
    ```
    quarkus.datasource.jdbc.url=jdbc:postgresql://localhost:5432/quarkus
    quarkus.datasource.username=quarkus
    quarkus.datasource.password=quarkus
    ```
  - MongoDB:  
    ```
    quarkus.mongodb.connection-string=mongodb://localhost:27017
    quarkus.mongodb.database=quarkus-app
    ```
- **Hibernate**:  
  - Geração automática de schema: `quarkus.hibernate-orm.database.generation=update`
  - Log de SQL: `quarkus.hibernate-orm.log.sql=true`
- **JWT**:  
  - `quarkus.security.jwt.enabled=true`
  - `mp.jwt.verify.publickey.location=/META-INF/resources/publicKey.pem`
  - `mp.jwt.sign.key.location=privateKey.pem`
- **Porta HTTP**:  
  - `quarkus.http.port=8080`

---

## Testes

- Testes unitários e de integração com JUnit5, Mockito e Instancio.
- Para rodar os testes:
  ```sh
  ./mvnw test
  ```

---

## Dicas de Desenvolvimento

- **Injeção de Dependências**: Use sempre CDI (`@Inject`, `@ApplicationScoped`, etc) para facilitar testes e manutenção.
- **Mapeamento DTO/Entidade**: Centralize o uso do ModelMapper para evitar duplicidade de lógica.
- **Transações**: Use `@Transactional` em métodos de escrita no banco.
- **Senhas**: Nunca armazene senhas em texto puro. Sempre utilize BCrypt.
- **Validação**: Use DTOs e validadores para garantir integridade dos dados recebidos.

---

## Referências

- [Quarkus - Documentação Oficial](https://quarkus.io/guides/)
- [ModelMapper](http://modelmapper.org/)
- [BCrypt](https://www.mindrot.org/projects/jBCrypt/)
- [JWT](https://jwt.io/)

---

> **Observação:**  
> Para detalhes de cada classe, consulte os arquivos em `src/main/java/org/example/`.  
> Para dúvidas ou sugestões, abra uma issue no repositório. 