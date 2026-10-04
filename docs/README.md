# Documentação

## Como rodar

```bash
cp .env.example .env
docker compose up --build
```

- Frontend: http://localhost:5173
- Backend: http://localhost:8080/health
- PostgreSQL: localhost:5432

> O wrapper do Gradle (`gradlew`) não está incluído; o Dockerfile usa a imagem oficial do Gradle. Para rodar localmente, execute `gradle wrapper` em `backend/`.
