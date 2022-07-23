# Sophisticated Application Gateway and Load Balancer for HTTP 

An application gateway was implemented, it operates exclusively with the HTTP/1.1 protocol and which is capable of responding to multiple requests at the same time, using a dynamic pool of N high-performance servers. This implied the creation of a new PDU, as well as the use of TCP and UDP sockets.
A load balancing mechanism was also implemented in order to preserve the scalability and availability, allowing the distribution of requests from one server to many others.

The project was implemented using \textbf{Java}, based mainly on the respective Sockets and Concurrency libraries.

It was developed in **Computer Communications**, in the second semester of the 3rd year of the Bachelor's degree (2020/21).

### Content

1. [Assignment](assignment.pdf)
2. [Project](project)
3. [Report](report.pdf)
