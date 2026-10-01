FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm install --no-audit --no-fund
COPY . .
RUN npx ng build --configuration production

FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/hackathon-starter/browser /usr/share/nginx/html
EXPOSE 80
