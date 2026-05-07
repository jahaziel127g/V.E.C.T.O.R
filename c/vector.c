/* V.E.C.T.O.R - Minimal C version */
/* Build: gcc -o vector_c vector.c -lcurl -ljansson -lpthread -O2 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <pthread.h>
#include <curl/curl.h>
#include <jansson.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

#define PORT 8080
#define OLLAMA_URL "http://localhost:11434"
#define MODEL "gemma3:1b-it-qat"
#define BUF_SIZE 16384
#define MAX_THREADS 4

typedef struct {
    char *memory;
    size_t size;
} ResponseBuffer;

static size_t write_cb(void *data, size_t size, size_t nmemb, void *userp) {
    size_t realsize = size * nmemb;
    ResponseBuffer *mem = (ResponseBuffer *)userp;
    char *ptr = realloc(mem->memory, mem->size + realsize + 1);
    if(!ptr) return 0;
    mem->memory = ptr;
    memcpy(&(mem->memory[mem->size]), data, realsize);
    mem->size += realsize;
    mem->memory[mem->size] = 0;
    return realsize;
}

/* Preload model */
void preload_model() {
    CURL *curl = curl_easy_init();
    if(!curl) return;
    
    json_t *req = json_pack("{s:s, s:s, s:b, s:{s:i}}",
        "model", MODEL,
        "prompt", "hi",
        "stream", json_false(),
        "options", "num_predict", 10);
    
    char *req_str = json_dumps(req, 0);
    json_decref(req);
    
    ResponseBuffer resp = {0};
    curl_easy_setopt(curl, CURLOPT_URL, OLLAMA_URL "/api/generate");
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, req_str);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &resp);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 60L);
    
    struct curl_slist *headers = NULL;
    headers = curl_slist_append(headers, "Content-Type: application/json");
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    
    CURLcode res = curl_easy_perform(curl);
    if(res == CURLE_OK) {
        printf("Model preloaded successfully!\n");
    } else {
        printf("Preload failed: %s\n", curl_easy_strerror(res));
    }
    
    free(req_str);
    free(resp.memory);
    curl_slist_free_all(headers);
    curl_easy_cleanup(curl);
}

/* Handle one request */
void *handle_req(void *arg) {
    int client_fd = *(int *)arg;
    char buf[BUF_SIZE] = {0};
    read(client_fd, buf, BUF_SIZE - 1);
    
    /* Extract question from JSON */
    char *q_start = strstr(buf, "\"question\":");
    if(!q_start) { close(client_fd); return NULL; }
    q_start += 11;
    while(*q_start == ' ' || *q_start == '"') q_start++;
    char *q_end = strchr(q_start, '"');
    if(!q_end) { close(client_fd); return NULL; }
    *q_end = 0;
    
    /* Call Ollama */
    CURL *curl = curl_easy_init();
    json_t *req = json_pack("{s:s, s:s, s:b, s:{s:f, s:f, s:i, s:i}}",
        "model", MODEL,
        "prompt", q_start,
        "stream", json_false(),
        "options", "temperature", 0.7, "top_p", 0.9, "num_predict", 150, "num_ctx", 256);
    
    char *req_str = json_dumps(req, 0);
    json_decref(req);
    
    ResponseBuffer resp = {0};
    curl_easy_setopt(curl, CURLOPT_URL, OLLAMA_URL "/api/generate");
    curl_easy_setopt(curl, CURLOPT_POSTFIELDS, req_str);
    curl_easy_setopt(curl, CURLOPT_WRITEFUNCTION, write_cb);
    curl_easy_setopt(curl, CURLOPT_WRITEDATA, &resp);
    curl_easy_setopt(curl, CURLOPT_TIMEOUT, 60L);
    
    struct curl_slist *headers = NULL;
    headers = curl_slist_append(headers, "Content-Type: application/json");
    curl_easy_setopt(curl, CURLOPT_HTTPHEADER, headers);
    
    curl_easy_perform(curl);
    
    /* Parse response */
    char *answer = "Error";
    if(resp.memory) {
        json_error_t error;
        json_t *root = json_loads(resp.memory, 0, &error);
        if(root) {
            json_t *ans = json_object_get(root, "response");
            if(ans) answer = (char *)json_string_value(ans);
        }
    }
    
    /* Send response */
    char response[BUF_SIZE];
    snprintf(response, BUF_SIZE,
        "HTTP/1.1 200 OK\r\n"
        "Content-Type: application/json\r\n\r\n"
        "{\"answer\":\"%s\",\"model\":\"%s\",\"source\":\"local model\",\"complexity\":\"simple\",\"processing_time_ms\":0}",
        answer, MODEL);
    
    write(client_fd, response, strlen(response));
    
    free(req_str);
    free(resp.memory);
    curl_slist_free_all(headers);
    curl_easy_cleanup(curl);
    close(client_fd);
    return NULL;
}

int main() {
    curl_global_init(CURL_GLOBAL_ALL);
    
    printf("V.E.C.T.O.R C starting on http://localhost:%d\n", PORT);
    
    /* Create server socket */
    int server_fd = socket(AF_INET, SOCK_STREAM, 0);
    int opt = 1;
    setsockopt(server_fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    
    struct sockaddr_in addr;
    memset(&addr, 0, sizeof(addr));
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = htonl(INADDR_ANY);
    addr.sin_port = htons(PORT);
    
    bind(server_fd, (struct sockaddr *)&addr, sizeof(addr));
    listen(server_fd, 10);
    
    while(1) {
        int client_fd = accept(server_fd, NULL, NULL);
        pthread_t tid;
        pthread_create(&tid, NULL, handle_req, &client_fd);
        pthread_detach(tid);
    }
    
    close(server_fd);
    curl_global_cleanup();
    return 0;
}
