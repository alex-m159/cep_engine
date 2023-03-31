import log from 'loglevel'


class Logger {
    
    printer: Function

    constructor(printer: Function) {
        this.printer = printer
    }

    log(s: string): any {
        this.printer(s)
    }

    info(s: string): any {
        this.printer(`[INFO] ${s}`)
    }

    debug(s: string) {
        this.printer(`[DEBUG] ${s}`)
    }

    error(s: string) {
        this.printer(`[ERROR] ${s}`)
    }
}

log.enableAll(false)
export const logger = new Logger(log.log)