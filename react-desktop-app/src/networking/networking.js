const ip = "localhost";
const port = 8082;

const fetchData = (api) => (
    fetch(`http://${ip}:${port}/${api}`)
        .then(result => result.json())
);

const fetchDataPut = (api, body) => (
    fetch(`http://${ip}:${port}/${api}`, {
        method: "PUT",
        body: JSON.stringify(body),
        mode: "cors",
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json;charset=UTF-8'
        }
    })
);

export {
    fetchData,
    fetchDataPut
}