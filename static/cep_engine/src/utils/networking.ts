import {backendIp} from '../config'


export function call(route: string, method: string = "GET", body?: Object | string, headers?: Map<string, string>): Promise<Response> {
    let b: string = body instanceof String ? String(body) : JSON.stringify(body)
    let options = {
        method: method,
        headers: new Headers(),
        body: b
    }
    options.headers.set('Content-Type', 'application/json')
    options.headers.set('Sec-Fetch-Site', 'cross-site')
    headers?.forEach((v, k) => options.headers.set(k, v))
    return fetch(`${backendIp}${route}`, options)}

export default call