import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    insecureSkipTLSVerify: true, 
    stages: [
        { duration: '10s', target: 50 }, // Ramp-up: 10 VUs over 10 seconds
        { duration: '30s', target: 50 }, // Steady state: 10 VUs for 30 seconds
        { duration: '10s', target: 0 }   // Ramp-down: 0 VUs over 10 seconds
    ],
};

export default function () {
    let res = http.get('https://localhost:7143/payload.html');

    check(res, {
        'is status 200': (r) => r.status === 200
    });

    sleep(1); // Wait for 1 second between iterations
}
