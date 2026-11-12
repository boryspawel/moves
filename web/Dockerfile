FROM node:22.22.3-alpine AS build
WORKDIR /workspace
COPY package.json package-lock.json ./
# Match packageManager in package.json; npm 10 rejects this npm 11 lockfile.
RUN npm install --global npm@11.12.1 && npm ci
COPY . .
RUN npm run build

FROM nginx:1.27.5-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/web/browser /usr/share/nginx/html
COPY docker-entrypoint.d/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
RUN chmod 755 /docker-entrypoint.d/40-runtime-config.sh
EXPOSE 8080
