package br.petersonaraujo.msorder.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL) // Oculta a lista de campos se ela estiver vazia
public class ErrorResponse {
    private LocalDateTime dataHora;
    private int status;
    private String erro;
    private String mensagem;
    private List<CampoErro> campos;

    public ErrorResponse(int status, String erro, String mensagem, List<CampoErro> campos) {
        this.dataHora = LocalDateTime.now();
        this.status = status;
        this.erro = erro;
        this.mensagem = mensagem;
        this.campos = campos;
    }

    public LocalDateTime getDataHora() { return dataHora; }
    public int getStatus() { return status; }
    public String getErro() { return erro; }
    public String getMensagem() { return mensagem; }
    public List<CampoErro> getCampos() { return campos; }

    public static class CampoErro {
        private String campo;
        private String mensagem;

        public CampoErro(String campo, String mensagem) {
            this.campo = campo;
            this.mensagem = mensagem;
        }

        public String getCampo() { return campo; }
        public String getMensagem() { return mensagem; }
    }
}