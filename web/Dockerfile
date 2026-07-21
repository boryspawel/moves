FROM node:22.22.3-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
ARG KEYCLOAK_URL
RUN test -n "$KEYCLOAK_URL" \
    && sed -i "s|__KEYCLOAK_URL__|$KEYCLOAK_URL|g" src/environments/environment.production.ts \
    && npm run build

FROM nginx:1.27.5-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/web/browser /usr/share/nginx/html
EXPOSE 8080
