#include <stdio.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <unistd.h>

int main() {
    int fd = socket(AF_INET, SOCK_STREAM, 0);
    int opt = 1;
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    
    struct sockaddr_in addr;
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons(8080);
    
    bind(fd, (struct sockaddr *)&addr, sizeof(addr));
    listen(fd, 10);
    
    printf("Server started on port 8080\n");
    
    while(1) {
        int client = accept(fd, NULL, NULL);
        char *msg = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\n\r\nHello from C!";
        write(client, msg, strlen(msg));
        close(client);
    }
    return 0;
}
