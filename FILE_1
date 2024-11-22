# Guaranteed UDP (GUDP)

## TODO

- [ ] Add more testing cases
- [ ] Building and testing Java with Gradle

## Judging Criteria

### 1. GUDPSocket

```text
OK: Code compiles without error.
WARN: finish() needs to wait if senderList still have packets to be sent
### TEST1: Send multiple packets in-flight + check packet content (1.0p)
OK: GUDP version must be 1
OK: First packet is GUDP BSN (type 2)
OK: Sequence number is random and not zero or one
OK: BSN packet contains only GUDP header
OK: GUDP version must be 1
OK: Second packet is GUDP DATA (type 1)
OK: Sequence number should be random and not zero
OK: Second packet has an increment sequence number
OK: data packet seems to contain GUDP header + payload
TEST1: OK 1.0p
### TEST2: send and receive files with your code without loss (1.0p)
OK: Your code can send and receive one file
OK: Your code can send and receive multiple files
TEST2: OK 1.0p
### TEST3: send one file to other receiver without loss (0.5p)
OK: Your code can send one file to other receiver
TEST3: OK 0.5p
### TEST4: send one file to other receiver with loss (0.5p)
OK: Your code can send one file when first BSN is lost
OK: Your code can send one file when first DATA is lost
OK: Your code can send one file when first FIN is lost
OK: Your code can send one file when first ACK is lost
OK: Your code can send one file with random loss
TEST4: OK 0.5p
### TEST5: receive one file from other sender without loss (0.5p)
OK: Your code can receive one file from other sender
TEST5: OK 0.5p
### TEST6: receive one file from other sender with loss (0.5p)
OK: Your code can receive one file when first BSN is lost
OK: Your code can receive one file when first DATA is lost
OK: Your code can receive one file when first FIN is lost
OK: Your code can receive one file when first ACK is lost
OK: Your code can receive one file with random loss
TEST6: OK 0.5p
### TEST7: send one file to multiple receivers without loss (0.5p)
OK: Your code can send one file to multiple receivers
TEST7: OK 0.5p
### TEST8: send one file to multiple receivers with loss (0.5p)
OK: Your code can send one file to multiple receivers
TEST8: OK 0.5p
### TEST9: send multiple files to other receiver without loss (0.5p)
OK: Your code can send multiple files to other receiver
TEST9: OK 0.5p
### TEST10: send multiple files to other receiver with loss (0.5p)
OK: Your code can send multiple files to other receiver
TEST10: OK 0.5p
### TEST11: receive multiple files from other sender without loss (0.5p)
OK: Your code can receive one file from other sender
TEST11: OK 0.5p
### TEST12: receive multiple files from other sender with loss (0.5p)
OK: Your code can receive one file from other sender
TEST12: OK 0.5p
##########
IMPORTANT: You pass only if scores of TEST1-6 >=3.5 points and TEST1-12 >=5.0 points.
You get the scores only when you pass. Otherwise, you get 0 points
RESULTS: PASS
SCORE: 7.0
##########
```

### 2. SenderThread

```text
OK: Code compiles without error.
### TEST1: Send multiple packets in-flight + check packet content (1.0p)
OK: GUDP version must be 1
OK: First packet is GUDP BSN (type 2)
OK: Sequence number is random and not zero or one
OK: BSN packet contains only GUDP header
OK: GUDP version must be 1
OK: Second packet is GUDP DATA (type 1)
OK: Sequence number should be random and not zero
OK: Second packet has an increment sequence number
OK: data packet seems to contain GUDP header + payload
TEST1: OK 1.0p
### TEST2: send and receive files with your code without loss (1.0p)
ERROR: VSSend did not gracefully terminate after sending!
OK: Your code can send and receive one file
ERROR: VSSend did not gracefully terminate after sending!
OK: Your code can send and receive multiple files
TEST2: NOTOK 0.5p
### TEST3: send one file to other receiver without loss (0.5p)
OK: Your code can send one file to other receiver
TEST3: OK 0.5p
### TEST4: send one file to other receiver with loss (0.5p)
OK: Your code can send one file when first BSN is lost
OK: Your code can send one file when first DATA is lost
OK: Your code can send one file when first FIN is lost
OK: Your code can send one file when first ACK is lost
OK: Your code can send one file with random loss
TEST4: OK 0.5p
TEST5: SKIPPED 0.0p
TEST6: SKIPPED 0.0p
### TEST7: send one file to multiple receivers without loss (0.5p)
WARN: Your code did not terminate after sending
OK: Your code can send one file to multiple receivers
TEST7: OK 0.5p
### TEST8: send one file to multiple receivers with loss (0.5p)
OK: Your code can send one file to multiple receivers
TEST8: OK 0.5p
### TEST9: send multiple files to other receiver without loss (0.5p)
OK: Your code can send multiple files to other receiver
TEST9: OK 0.5p
### TEST10: send multiple files to other receiver with loss (0.5p)
OK: Your code can send multiple files to other receiver
TEST10: OK 0.5p
TEST11: SKIPPED 0.0p
TEST12: SKIPPED 0.0p
##########
IMPORTANT: You pass only if scores of TEST1-6 >=2.5 points and TEST1-12 >=3.5 points.
You get the scores only when you pass. Otherwise, you get 0 points
RESULTS: PASS
SCORE: 4.50
##########
```