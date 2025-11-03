spring-boot-login-backend

Fully dockerized spring boot app with postgres and spring security with JWT 

#
To clear database volume: 
docker-compose down -v

To run the app:
docker-compose up --build -d

run test-auth.sh to test the endpoints or postman
chmod +x test-auth.sh
./test-auth.sh