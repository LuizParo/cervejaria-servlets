package br.com.cervejaria.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLStreamReader;

import org.apache.commons.io.IOUtils;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.mapped.MappedNamespaceConvention;
import org.codehaus.jettison.mapped.MappedXMLStreamReader;
import org.codehaus.jettison.mapped.MappedXMLStreamWriter;

import br.com.cervejaria.exception.RecursoSemIdentificadorException;
import br.com.cervejaria.model.Cerveja;
import br.com.cervejaria.model.Estoque;
import br.com.cervejaria.model.rest.Cervejas;

@WebServlet(value = "/cervejas/*")
public class CervejaServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final JAXBContext CONTEXT;
    private Estoque estoque = new Estoque();
    
    static {
        try {
            CONTEXT = JAXBContext.newInstance(Cervejas.class);
        } catch (JAXBException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
    
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String identificador = null;
        try {
            identificador = obtemIdentificador(req);
        } catch (RecursoSemIdentificadorException e) {
            // Manda um erro 400 - Bad Request
            resp.sendError(400, e.getMessage());
        }
        
        if(identificador != null && this.estoque.recuperarCervejaPeloNome(identificador) != null) {
            resp.sendError(409, "Já existe uma cerveja com esse nome");
            return;
        }
        
        try {
            String contentType = req.getContentType();
            if(contentType == null || contentType.equals("text/xml") || contentType.equals("application/xml")) {
                Unmarshaller unmarshaller = CONTEXT.createUnmarshaller();
                Cerveja cerveja = (Cerveja) unmarshaller.unmarshal(req.getInputStream());
                cerveja.setNome(identificador);
                
                this.estoque.adicionarCerveja(cerveja);
                
                String requestURI = req.getRequestURI();
                resp.setHeader("Location", requestURI);
                resp.setStatus(201);
                this.escreveXML(req, resp);
            } else if (contentType.equals("application/json")) {
                StringBuilder builder = new StringBuilder();
                IOUtils.readLines(req.getInputStream()).forEach(builder::append);
                
                MappedNamespaceConvention con = new MappedNamespaceConvention();
                JSONObject jsonObject = new JSONObject(builder.toString());
                XMLStreamReader xmlStreamReader = new MappedXMLStreamReader(jsonObject, con);
                
                Unmarshaller unmarshaller = CONTEXT.createUnmarshaller();
                Cerveja cerveja = (Cerveja) unmarshaller.unmarshal(xmlStreamReader);
                cerveja.setNome(identificador);
                
                this.estoque.adicionarCerveja(cerveja);
                
                String requestURI = req.getRequestURI();
                resp.setHeader("Location", requestURI);
                resp.setStatus(201);
                this.escreveJSON(req, resp);
            } else {
                // O header accept foi recebido com um valor não suportado
                resp.sendError(415); // Formato não suportado
            }
        } catch (Exception e) {
            e.printStackTrace();
            resp.sendError(500, e.getMessage());
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String acceptHeader = req.getHeader("Accept");
        
        if(acceptHeader == null || acceptHeader.contains("application/xml")) {
            this.escreveXML(req, resp);
        } else if(acceptHeader.contains("application/json")) {
            this.escreveJSON(req, resp);
        } else {
            // O header accept foi recebido com um valor não suportado
            resp.sendError(415); // Formato não suportado
        }
    }

    private void escreveJSON(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        Object objetoASerEnviado = this.localizaObjetoASerEnviado(req);
        
        if(objetoASerEnviado == null) {
            resp.sendError(404); // Objeto não encontrado.
            return;
        }
        
        try {
            resp.setContentType("application/json;charset=UTF-8");
            
            MappedNamespaceConvention namespaceConvention = new MappedNamespaceConvention();
            MappedXMLStreamWriter xmlStreamWriter = new MappedXMLStreamWriter(namespaceConvention, resp.getWriter());
            
            Marshaller marshaller = CONTEXT.createMarshaller();
            marshaller.marshal(objetoASerEnviado, xmlStreamWriter);
        } catch (JAXBException e) {
            e.printStackTrace();
            resp.sendError(500, e.getMessage());
        }
    }

    private void escreveXML(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        try {
            Object objetoASerEnviado = this.localizaObjetoASerEnviado(req);
            
            if(objetoASerEnviado == null) {
                resp.sendError(404); // Objeto não encontrado.
                return;
            }
            
            resp.setContentType("application/xml;charset=UTF-8");
            PrintWriter out = resp.getWriter();

            Marshaller marshaller = CONTEXT.createMarshaller();
            marshaller.marshal(objetoASerEnviado, out);
        } catch (JAXBException e) {
            e.printStackTrace();
            resp.sendError(500, e.getMessage());
        }
    }
    
    @SuppressWarnings("deprecation")
    private String obtemIdentificador(HttpServletRequest req) {
        String requestUri = req.getRequestURI();
        String[] pedacosDaUri = requestUri.split("/");
        
        boolean contextoCervejasEncontrado = false;
        for (String contexto : pedacosDaUri) {
            if(contexto.equals("cervejas")) {
                contextoCervejasEncontrado = true;
                continue;
            }
            
            if(contextoCervejasEncontrado) {
                try {
                    return URLDecoder.decode(contexto, "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    return URLDecoder.decode(contexto);
                }
            }
        }
        
        throw new RecursoSemIdentificadorException("Recurso sem identificador");
    }
    
    private Object localizaObjetoASerEnviado(HttpServletRequest req) {
        try {
            String identificador = this.obtemIdentificador(req);
            return this.estoque.recuperarCervejaPeloNome(identificador);
        } catch (RecursoSemIdentificadorException e) {
            Cervejas cervejas = new Cervejas();
            cervejas.setCervejas(new ArrayList<>(estoque.listarCervejas()));
            return cervejas;
        }
    }
}