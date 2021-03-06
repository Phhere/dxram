# Network request timeouts

If you are experiencing network request timeouts but the target node to send the request to is available and some requests are sent successfully, try increasing the request timeout in the DXRAM configuration file. This might happen if the system sends requests with huge payloads resulting in a higher transmission time and exceeding the set timeout.  You can find the entry
```JSON
"m_requestTimeout": {
    "m_value": 333,
    "m_unit": "ms"
}
```
under the *NetworkComponent* section.

Furthermore, this can also happen on very high loads (sending too many messages and the other node can't keep up with processing). Depending on your application/use case, you can ignore these errors or handle them accordingly by retrying the transmission.

# Ethernet network TCP buffer sizes

If you get the warning
```
Send buffer size could not be set properly. Check OS settings! Requested: XXX, actual: XXX
```
you should consider setting the TCP buffer sizes (see */proc/sys/net/core*) to the requested value. DXRAM does still work without setting them to the requested value but you won't get the best network performance.

# Network error "Could not bind network address"

It's very likely that you are already running a DXRAM instance or another application which uses the current port
assigned. Either shotdown that process or use another port for the DXRAM instance you want to start.
>>>>>>> doc: Update troubleshooting
