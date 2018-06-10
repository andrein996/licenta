const ip = "localhost";
const port = 8082;

const fetchData = (api) => (
    fetch(`http://${ip}:${port}/${api}`)
        .then(result => result.json())
);

const fetchDataPostWithBody = (api, body) => (
    fetch(`http://${ip}:${port}/${api}`, {
        body: JSON.stringify(body),
        method: "POST",
        mode: "cors",
        headers: {
            'Accept': 'application/json',
            'Content-Type': 'application/json;charset=UTF-8'
        }
    }).then(result => result.json())
);

const fetchDataPut = (api) => (
    fetch(`http://${ip}:${port}/${api}`, {
        method: "PUT",
        mode: "cors"
    })
);

export {
    fetchData,
    fetchDataPostWithBody,
    fetchDataPut
}